package top.levitatemedia.renzo.hub.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import okhttp3.Request

/** The two halves of the Hub. */
@Serializable
enum class HubTarget { Renzo, Shiori }

/**
 * Signs an outbound request for one half of the Hub.
 *
 * This exists for ONE reason: Coil has a single process-wide ImageLoader, but
 * the two halves authenticate differently — Renzo replays an opaque
 * `fsa_session` cookie, Shiori sends `Authorization: Bearer` (or `X-Renzo-User`
 * when the server runs with auth disabled). Whichever Application installed
 * its own loader would break the other half's images.
 *
 * Each feature registers its signer at startup; the shared loader asks
 * whichever half is currently on screen to sign. Only one is ever visible, so a
 * single "active" pointer is sufficient — this is deliberately NOT an attempt
 * to unify the two REST stacks, which stay separate and working.
 */
fun interface RequestSigner {
    fun sign(builder: Request.Builder)
}

object HubSession {
    @Volatile
    private var active: HubTarget? = null

    private val signers = java.util.concurrent.ConcurrentHashMap<HubTarget, RequestSigner>()

    fun register(target: HubTarget, signer: RequestSigner) {
        signers[target] = signer
    }

    /** Called when a half comes to the foreground. */
    fun setActive(target: HubTarget) {
        active = target
    }

    fun activeTarget(): HubTarget? = active

    /** Applies the active half's credentials, if it has registered any. */
    fun signActive(builder: Request.Builder) {
        active?.let { signers[it] }?.sign(builder)
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
        signers[target]?.sign(builder)
    }

    fun hasSigner(target: HubTarget): Boolean = signers.containsKey(target)

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
