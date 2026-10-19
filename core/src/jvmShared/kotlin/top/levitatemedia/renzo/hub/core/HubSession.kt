package top.levitatemedia.renzo.hub.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

// HubTarget itself lives in commonMain (HubTarget.kt) — the offline models
// reference it without touching OkHttp.

/**
 * Signs an outbound request for one half of the Hub.
 *
 * This exists for ONE reason: Coil has a single process-wide ImageLoader, but
 * the two halves authenticate differently — Renzo replays an opaque
 * `fsa_session` cookie, Shiori sends `Authorization: Bearer` (or `X-Renzo-User`
 * when the server runs with auth disabled). Whichever Application installed
 * its own loader would break the other half's images.
 *
 * Each feature registers its signer at startup; the shared clients ask the
 * half whose server a request is going to to sign it. This is deliberately NOT
 * an attempt to unify the two REST stacks, which stay separate and working.
 */
fun interface RequestSigner {
    fun sign(builder: Request.Builder)

    /**
     * Sees the response to every request this half signed, whichever client
     * sent it.
     *
     * The anime half needs this. Its session cookie ROTATES once a day, and the
     * replacement rides on whichever response happens to be first after the
     * rotation falls due: as often a poster, a video range or a background
     * download as an API call. Only the API client used to read it, so a
     * replacement landing anywhere else was dropped. The app kept presenting the
     * old cookie, which the server honours for one more day, and the next launch
     * after that was a sign-in.
     */
    fun onResponse(response: Response) {}

    /**
     * Swap a credential the server just refused for a fresh one, if this half
     * has a way to. True when [rejected] is worth sending again.
     *
     * Blocking, and never called on the main thread: it may make a request.
     */
    fun renew(rejected: Request): Boolean = false
}

object HubSession {
    @Volatile
    private var active: HubTarget? = null

    private class Half(val server: () -> String?, val signer: RequestSigner)

    private val halves = java.util.concurrent.ConcurrentHashMap<HubTarget, Half>()

    /**
     * @param server the half's server address as the user entered it, read at
     *   request time, so a change of server needs no re-registration.
     */
    fun register(target: HubTarget, server: () -> String?, signer: RequestSigner) {
        halves[target] = Half(server, signer)
    }

    /** Called when a half comes to the foreground. */
    fun setActive(target: HubTarget) {
        active = target
    }

    fun activeTarget(): HubTarget? = active

    /**
     * The half whose server [url] is on, or null for any other host.
     *
     * Null is the important answer. The shared image loader used to sign every
     * request with whichever half was on screen, so AniList's poster CDN got
     * the Renzo session cookie and a manga source's thumbnail host got a Shiori
     * token. Credentials go to the user's own servers and nowhere else, which
     * the downloader and the player already enforced.
     *
     * Matched on host alone, like the downloader: the scheme or port in a
     * server-built URL can differ from the one typed in. Two halves served from
     * one host is ambiguous, and the half on screen wins, as before.
     */
    fun targetFor(url: HttpUrl): HubTarget? {
        val matches = halves.entries
            .filter { (_, half) -> hostOf(half.server())?.equals(url.host, ignoreCase = true) == true }
            .map { it.key }
        return when (matches.size) {
            0 -> null
            1 -> matches[0]
            else -> active?.takeIf { it in matches } ?: matches[0]
        }
    }

    /**
     * Applies a specific half's credentials regardless of what is on screen.
     *
     * The downloader needs this: a queued job keeps running while the user
     * switches to the other app, or with no UI at all. Signing per-request also
     * means a job never carries a stale token — the standalone Shiori service
     * baked one into the job payload, which expired on a long queue.
     */
    fun signFor(target: HubTarget, builder: Request.Builder) {
        halves[target]?.signer?.sign(builder)
    }

    /** Hand a response to the half that signed its request. See [RequestSigner.onResponse]. */
    fun observe(target: HubTarget, response: Response) {
        halves[target]?.signer?.let { runCatching { it.onResponse(response) } }
    }

    /** See [RequestSigner.renew]. False when the half has no way to renew. */
    fun renew(target: HubTarget, rejected: Request): Boolean =
        halves[target]?.signer?.let { runCatching { it.renew(rejected) }.getOrDefault(false) } ?: false

    fun hasSigner(target: HubTarget): Boolean = halves.containsKey(target)

    /**
     * For clients both halves share (the image loader): sign for whichever
     * half owns the host, let it see the response, and after a 401 renew once
     * and retry. Requests to any other host pass through untouched.
     */
    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val target = targetFor(request.url) ?: return@Interceptor chain.proceed(request)
        val signed = request.newBuilder().also { signFor(target, it) }.build()
        val response = chain.proceed(signed)
        observe(target, response)
        if (response.code != 401 || !renew(target, signed)) return@Interceptor response
        response.close()
        val again = chain.proceed(request.newBuilder().also { signFor(target, it) }.build())
        observe(target, again)
        again
    }

    private fun hostOf(server: String?): String? {
        val s = server?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return (if (s.startsWith("http", ignoreCase = true)) s else "https://$s").toHttpUrlOrNull()?.host
    }

    /**
     * Fires when a half's credentials are rejected, so the UI can return to its
     * login gate instead of leaving the user on a library that silently renders
     * nothing.
     *
     * A SharedFlow, not a StateFlow: this is an event. A replayed "you were
     * logged out" would kick a freshly signed-in user straight back out.
     */
    private val _unauthorized = MutableSharedFlow<HubTarget>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val unauthorized: SharedFlow<HubTarget> = _unauthorized.asSharedFlow()

    /**
     * Report a rejected credential. Called from each half's single HTTP choke
     * point, so no screen has to remember to handle 401 itself — which is
     * exactly how the blank-page behaviour arose.
     *
     * Must NOT be called for a failed sign-in attempt: the login endpoint
     * answers 401 for a wrong password, and reporting that would bounce the
     * user off the very form they are trying to use.
     */
    fun reportUnauthorized(target: HubTarget) {
        _unauthorized.tryEmit(target)
    }
}
