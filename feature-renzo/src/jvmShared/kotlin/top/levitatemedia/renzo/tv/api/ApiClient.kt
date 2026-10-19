package top.levitatemedia.renzo.tv.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.tv.Prefs
import java.util.concurrent.TimeUnit

class ApiError(val status: Int, message: String) : Exception(message)

private val SESSION_COOKIE = Regex("^fsa_session=([^;]*)")

/**
 * HTTP layer. Auth is the server's opaque `fsa_session` cookie, captured from
 * login's Set-Cookie and replayed on every request (the server's CSRF guard
 * only rejects browser-y Sec-Fetch-Site headers, which OkHttp never sends).
 *
 * GET /api/titles/:id/play/:ep BLOCKS server-side up to ~45s while the debrid
 * pipeline resolves — the read timeout is 75s for headroom.
 */
class ApiClient(private val prefs: Prefs) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        // Identify the Hub (see core HUB_USER_AGENT) — servers label the
        // session's device off the login request's User-Agent.
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", top.levitatemedia.renzo.hub.core.HUB_USER_AGENT)
                    .build(),
            )
        }
        // On the client, not in raw(): the player streams through this same
        // client, and its responses carry the rotated cookie as often as the
        // API's do. See captureSession.
        .addInterceptor { chain ->
            chain.proceed(chain.request()).also { captureSession(it) }
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun base(): String = prefs.serverUrl ?: throw ApiError(0, "No server configured")

    /** Absolute URL for a server-relative path ("/files/..", "/api/captions/.."). */
    fun absolute(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else base() + path

    fun cookieHeader(): String? = prefs.sessionCookie?.let { "fsa_session=$it" }

    /**
     * Keeps a rotated session cookie from [res], whichever request it answers.
     *
     * The server replaces the cookie once a day, on the first request after
     * the replacement falls due. It honours the old one for a day more, and
     * answers any request still carrying it with the new one again. Only
     * API responses used to be read, so a replacement that came back on a
     * poster, a video range or a background download was lost. If the app then
     * made no API call within that day (on Android, a day closed was enough),
     * the old cookie expired and the next launch was a sign-in.
     *
     * Only the user's own server is listened to: the player sends this client
     * to debrid CDNs too, and no other host gets to set this session.
     */
    fun captureSession(res: okhttp3.Response) {
        val server = prefs.serverUrl?.toHttpUrlOrNull()?.host ?: return
        if (!res.request.url.host.equals(server, ignoreCase = true)) return
        res.headers("Set-Cookie").forEach { c ->
            val v = SESSION_COOKIE.find(c)?.groupValues?.get(1) ?: return@forEach
            if (v.isEmpty()) prefs.clearSession() else prefs.sessionCookie = v
        }
    }

    private fun request(path: String, method: String, body: String?, session: String?): Request {
        val b = Request.Builder().url(base() + path)
        session?.let { b.header("Cookie", "fsa_session=$it") }
        when (method) {
            "GET" -> b.get()
            else -> b.method(method, (body ?: "{}").toRequestBody("application/json".toMediaType()))
        }
        return b.build()
    }

    /**
     * Execute and return the raw body. Throws ApiError(status) on non-2xx with
     * the server's {error} message; status 0 = network unreachable.
     */
    suspend fun raw(path: String, method: String = "GET", body: String? = null): String =
        withContext(Dispatchers.IO) { send(path, method, body, retried = false) }

    private fun send(path: String, method: String, body: String?, retried: Boolean): String {
        val sent = prefs.sessionCookie
        val res = try {
            http.newCall(request(path, method, body, sent)).execute()
        } catch (e: Exception) {
            throw ApiError(0, e.message ?: "network error")
        }
        res.use {
            val text = it.body?.string() ?: ""
            if (!it.isSuccessful) {
                // One choke point for the whole half: a rejected session
                // returns the user to the login gate instead of leaving
                // every screen to render an empty list on its own.
                //
                // The login endpoint is excluded — it answers 401 for a
                // wrong password, and bouncing off the login form while
                // trying to log in would be absurd.
                if (it.code == 401 && !path.startsWith("/api/auth/login")) {
                    val now = prefs.sessionCookie
                    // A newer cookie landed while this request was out (another
                    // response carried the rotation). The 401 is about the
                    // cookie this request carried, not the one now held: try
                    // again with that one rather than wiping it.
                    if (!retried && now != null && now != sent) return send(path, method, body, retried = true)
                    // Clear only what was refused. Wiping unconditionally could
                    // throw away a good cookie that arrived in the meantime.
                    if (now == sent) prefs.clearSession()
                    HubSession.reportUnauthorized(HubTarget.Renzo)
                }
                val msg = try {
                    Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)
                } catch (_: Exception) { null }
                throw ApiError(it.code, msg ?: "HTTP ${it.code}")
            }
            return text
        }
    }

    suspend inline fun <reified T> get(path: String): T = json.decodeFromString(raw(path))

    suspend inline fun <reified T> post(path: String, body: String? = null): T =
        json.decodeFromString(raw(path, "POST", body))

    /** Unauthenticated reachability probe: a Renzo server answers /version with a build id. */
    suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = http.newCall(Request.Builder().url("$url/version").get().build()).execute()
            res.use { it.isSuccessful && (it.body?.string() ?: "").contains("\"build\"") }
        } catch (_: Exception) { false }
    }
}
