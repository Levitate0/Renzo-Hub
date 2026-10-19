package app.renzoshiori.client.data.network

import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import app.renzoshiori.client.data.auth.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * `coerceInputValues` is load-bearing, not tidiness: plenty of backend DTOs
 * declare non-nullable C# reference types with no initializer (ExtensionDto's
 * `package`/`thumbnailUrl`, ExtensionRepositoryDto's `name`/`id`, …), so the
 * wire can legitimately carry `null` where the Kotlin field is a non-null
 * `String` with a default. Without coercion kotlinx throws and the whole
 * screen fails to load; with it, null falls back to the declared default.
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    coerceInputValues = true
    explicitNulls = false
}

/**
 * In-memory cookie jar scoped to exactly the one cookie the backend actually
 * needs from a native client: the httpOnly `refresh_token` set by
 * POST /api/auth/login (rememberMe=true), read back by POST /api/auth/refresh.
 * Everything else is stateless Bearer auth via [AuthInterceptor] — no full
 * cookie-jar/session model needed.
 */
private class RefreshCookieJar(private val tokenStore: TokenStore) : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Merged by name, not replaced wholesale. Replacing meant any response
        // that set some OTHER cookie for this host (a proxy's, a CDN's) dropped
        // the refresh cookie from the live jar, and the next refresh then went
        // out without it.
        val now = System.currentTimeMillis()
        store.compute(url.host) { _, old ->
            (old.orEmpty().filter { o -> cookies.none { it.name == o.name } } + cookies)
                .filter { it.value.isNotEmpty() && it.expiresAt > now }
        }
        // Persist the remember-me cookie so it survives the process. Kept in the
        // encrypted store, not here, because this jar dies with the app.
        //
        // A deletion arrives as this same cookie with an EMPTY value (the server
        // clears it by expiring it), so the value is checked rather than just its
        // presence: storing "" would leave a token that is not null and not
        // usable, and the next refresh would spend a round trip proving it.
        cookies.firstOrNull { it.name == REFRESH_COOKIE }?.let {
            tokenStore.refreshCookie = it.value.takeIf { value -> value.isNotBlank() && it.expiresAt > now }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val live = store[url.host].orEmpty().filter { it.expiresAt > now }
        if (live.any { it.name == REFRESH_COOKIE }) return live
        // First request after a cold start: rebuild the refresh cookie from the
        // encrypted store, otherwise "Remember me" only lasted as long as the
        // process did.
        val saved = tokenStore.refreshCookie ?: return live
        val cookie = Cookie.Builder()
            .name(REFRESH_COOKIE)
            .value(saved)
            .domain(url.host)
            .path("/")
            .httpOnly()
            .build()
        return live + cookie
    }

    private companion object {
        const val REFRESH_COOKIE = "refresh_token"
    }
}

/**
 * Attaches `Authorization: Bearer <token>` to every request, including image
 * loads. When the server has authentication disabled there is no token at
 * all: the backend then resolves the caller from an `X-Renzo-User` header
 * (AuthMiddleware), which is exactly what the web apiClient sends in profile
 * mode — without it the app would talk to the server as an anonymous guest
 * and no per-user data (library, progress, sources) would resolve.
 */
internal class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken
        val original = chain.request()
        val request = original.newBuilder().apply {
            when {
                // The refresh call authenticates with the cookie alone. It is
                // only ever sent from here or from renewSession(), and in both
                // cases the token it would carry is the one that just failed.
                original.url.encodedPath == REFRESH_PATH -> Unit
                token != null -> addHeader("Authorization", "Bearer $token")
                else -> tokenStore.lastUsername?.takeIf { it.isNotBlank() }
                    ?.let { addHeader("X-Renzo-User", it) }
            }
        }.build()
        val response = chain.proceed(request)

        if (response.code != 401 || request.url.encodedPath.trimEnd('/') in NO_RENEW_PATHS)
            return response

        // Closed BEFORE the refresh goes out on this chain: OkHttp refuses a
        // second request while a response body is still open, and the refresh
        // then failed as if the server were down. The server's own 401 has an
        // empty body, which happens to escape that; a 401 with any body at all,
        // from a proxy or a controller, did not. A buffered copy stands in for
        // it in case the refresh is refused.
        val refused = response.buffered()

        // The access token is short-lived (the server's sessionExpirationHours,
        // 24 by default) REGARDLESS of "Remember me" — remember-me is carried by
        // the long-lived refresh cookie, and the client is supposed to trade it
        // for a fresh access token. Without this the user was signed out after a
        // day no matter what they ticked.
        when (val outcome = renew({ chain.proceed(it) }, request)) {
            is Renewal.Renewed -> {
                return chain.proceed(
                    request.newBuilder().header("Authorization", "Bearer ${outcome.token}").build(),
                )
            }
            // The server could not answer — restarting, behind a proxy that
            // returned 502, or the network dropped mid-refresh. That is NOT a
            // dead session: signing the user out here would turn a blip into a
            // full re-login, and would throw away a remember-me cookie that is
            // still perfectly valid.
            //
            // So it surfaces as what it is, a failure to reach the server. It
            // used to come back as the original 401, and a 401 is a definite
            // "no" to every caller, which is exactly what made the boot check
            // wipe a good session.
            Renewal.Unavailable ->
                throw java.io.IOException("Couldn't renew the session: the server didn't answer")
            // A definite refusal. Fall through and announce it.
            Renewal.Rejected -> Unit
        }

        // No refresh possible: the session really is over. Announced once,
        // app-wide, and tagged with WHICH half — the Hub holds two independent
        // sessions against two servers, so an expiry here must leave the anime
        // side signed in. The standalone client's signal is global because it
        // only ever had one session; lifting that as-is would sign the user out
        // of both.
        HubSession.reportUnauthorized(HubTarget.Shiori)
        return refused
    }

    /** A copy of this response with its body read into memory, the original closed. */
    private fun Response.buffered(): Response = use {
        val body = it.body
        val bytes = runCatching { body?.bytes() }.getOrNull() ?: ByteArray(0)
        it.newBuilder().body(bytes.toResponseBody(body?.contentType())).build()
    }

    /**
     * Trades the refresh cookie for a new access token. Single-flight: a burst
     * of concurrent 401s must not fire parallel refreshes, because the endpoint
     * ROTATES the refresh token and the losers would invalidate the winner.
     *
     * [send] makes the refresh call: `chain.proceed` from inside the
     * interceptor, a fresh call through the same client from renewSession().
     */
    fun renew(send: (Request) -> Response, original: Request): Renewal {
        synchronized(refreshLock) {
            // Someone else refreshed while this thread waited on the lock.
            val current = tokenStore.accessToken
            if (current != null && current != original.header("Authorization")?.removePrefix("Bearer ")) {
                return Renewal.Renewed(current)
            }
            // Nothing to refresh with: the session really is over.
            if (tokenStore.refreshCookie == null) return Renewal.Rejected

            val refreshUrl = original.url.newBuilder()
                .encodedPath(REFRESH_PATH)
                .query(null)
                .build()
            val request = Request.Builder()
                .url(refreshUrl)
                .post(ByteArray(0).toRequestBody(null, 0, 0))
                .build()

            val response = runCatching { send(request) }.getOrNull()
                ?: return Renewal.Unavailable
            response.use {
                if (!it.isSuccessful) {
                    // ONLY a definite refusal spends the cookie. Anything else —
                    // 5xx, a proxy error page, a server mid-restart — is a
                    // failure to answer, not an answer of "no".
                    return if (it.code == 401 || it.code == 403) {
                        tokenStore.refreshCookie = null
                        Renewal.Rejected
                    } else {
                        Renewal.Unavailable
                    }
                }
                val body = runCatching { it.body?.string() }.getOrNull()
                    ?: return Renewal.Unavailable
                // A 2xx with no token is a server fault, not a rejection; keep
                // the cookie so a later attempt can still succeed.
                val token = TOKEN_REGEX.find(body)?.groupValues?.getOrNull(1)
                    ?: return Renewal.Unavailable
                tokenStore.accessToken = token
                return Renewal.Renewed(token)
            }
        }
    }

    /** Outcome of a refresh attempt — "no" and "couldn't ask" are different. */
    sealed interface Renewal {
        data class Renewed(val token: String) : Renewal
        /** The server refused: the remember-me cookie is spent. */
        data object Rejected : Renewal
        /** The server could not be asked. The cookie survives. */
        data object Unavailable : Renewal
    }

    private companion object {
        val refreshLock = Any()

        const val REFRESH_PATH = "/api/auth/refresh"

        /**
         * The endpoints whose 401 is an answer about what was SUBMITTED — a
         * password, a reset token, a pairing code, the refresh cookie itself —
         * rather than about an expired access token. Exactly the server's public
         * auth routes (AuthMiddleware.IsPublicRoute).
         *
         * This used to be every path under /api/auth/, which took in
         * GET /api/auth/me: the call the app makes at launch to restore the
         * session. A launch more than a day after the last sign-in or refresh
         * meets an expired access token, so that call answered 401, and because
         * of the prefix it was never renewed. The app took the 401 as final and
         * wiped the session, remember-me cookie included. Android kills an idle
         * app, so there it happened on nearly every launch after a day away.
         */
        val NO_RENEW_PATHS = setOf(
            "/api/auth/login",
            "/api/auth/status",
            "/api/auth/select-user",
            REFRESH_PATH,
            "/api/auth/set-password",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/tv/code",
            "/api/auth/tv/poll",
        )

        /** The refresh response is `{ "token": "...", "user": { ... } }`. */
        val TOKEN_REGEX = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"")
    }
}

/**
 * Per-request timeout override. A few endpoints do all their work inline and
 * can run for minutes — `POST /api/scrobbler/matches/auto` walks the whole
 * library against the tracker's API before it answers — so they carry
 * `X-Renzo-Timeout: <seconds>` and this strips the header back off before the
 * request leaves. Without it those calls die on the default read timeout and
 * surface as a generic failure even though the server is working fine.
 */
const val TIMEOUT_HEADER = "X-Renzo-Timeout"

private class PerRequestTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val seconds = request.header(TIMEOUT_HEADER)?.toIntOrNull()
            ?: return chain.proceed(request)
        val stripped = request.newBuilder().removeHeader(TIMEOUT_HEADER).build()
        return chain
            .withReadTimeout(seconds, java.util.concurrent.TimeUnit.SECONDS)
            .withWriteTimeout(seconds, java.util.concurrent.TimeUnit.SECONDS)
            .proceed(stripped)
    }
}

/**
 * Rebuilds the Retrofit/OkHttp stack whenever the connected server changes —
 * the base URL isn't known at compile time, it's whatever the user entered on
 * the Connect screen (see ConnectScreen / SystemInfoApi.isRenzoServer).
 */
class NetworkModule(private val tokenStore: TokenStore) {
    private val cookieJar = RefreshCookieJar(tokenStore)
    private val auth = AuthInterceptor(tokenStore)

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // Identify the Hub (see core HUB_USER_AGENT): the server labels
            // sessions off the login UA — the OkHttp default read as
            // "Browser" in the devices list.
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", top.levitatemedia.renzo.hub.core.HUB_USER_AGENT)
                        .build(),
                )
            }
            .addInterceptor(auth)
            .addInterceptor(PerRequestTimeoutInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // Full bodies in debug so a failing request can actually be
                    // read off logcat — "it looked like it worked" is otherwise
                    // impossible to tell from "it was never sent". Release stays
                    // at BASIC: bodies carry tokens and library contents.
                    level = if (NetworkModule.logBodies) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.BASIC
                    }
                },
            )
            // OkHttp defaults to a 10s read timeout, which several endpoints
            // legitimately exceed: /api/provider/list walks the online
            // extension repositories, scans and imports do real work, and
            // stream/page pulls an image from the source site.
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun retrofitFor(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** Retrofit client for the currently-connected server (throws if none set yet). */
    fun apiFor(baseUrl: String): ApiService = retrofitFor(baseUrl).create(ApiService::class.java)

    /** Convenience for call sites that already have a connected TokenStore.serverUrl. */
    fun currentApi(): ApiService? = tokenStore.serverUrl?.let { apiFor(it) }

    /**
     * Domain-specific Retrofit interfaces (StatusApi, SourcesApi, AccountApi…)
     * live in their own files so they can grow independently of [ApiService].
     */
    inline fun <reified T> currentServiceOf(): T? =
        serverUrl?.let { retrofitFor(it).create(T::class.java) }

    val serverUrl: String? get() = tokenStore.serverUrl

    /**
     * Renews the access token after [rejected] came back 401 somewhere outside
     * this client: the downloader's, the image loader's. Same single flight and
     * the same "no" versus "couldn't ask" rule as the interceptor, with the
     * refresh sent through this client so the rotated cookie is kept.
     *
     * True when [rejected] is worth sending again. Blocking.
     */
    fun renewSession(rejected: Request): Boolean =
        when (auth.renew({ okHttpClient.newCall(it).execute() }, rejected)) {
            is AuthInterceptor.Renewal.Renewed -> true
            AuthInterceptor.Renewal.Unavailable -> false
            AuthInterceptor.Renewal.Rejected -> {
                HubSession.reportUnauthorized(HubTarget.Shiori)
                false
            }
        }

    companion object {
        /**
         * Full request/response bodies in the log. Set by each platform's app
         * bootstrap from its own debug signal (Android: BuildConfig.DEBUG) —
         * BuildConfig doesn't exist off Android, which is why this is a flag.
         */
        var logBodies: Boolean = false
    }
}
