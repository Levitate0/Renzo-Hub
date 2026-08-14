package top.levitatemedia.renzo.tv.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.tv.Prefs
import java.util.concurrent.TimeUnit

class ApiError(val status: Int, message: String) : Exception(message)

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

    private fun request(path: String, method: String, body: String?): Request {
        val b = Request.Builder().url(base() + path)
        cookieHeader()?.let { b.header("Cookie", it) }
        when (method) {
            "GET" -> b.get()
            else -> b.method(method, (body ?: "{}").toRequestBody("application/json".toMediaType()))
        }
        return b.build()
    }

    /**
     * Execute and return the raw body. Captures a rotated session cookie from
     * any Set-Cookie. Throws ApiError(status) on non-2xx with the server's
     * {error} message; status 0 = network unreachable.
     */
    suspend fun raw(path: String, method: String = "GET", body: String? = null): String =
        withContext(Dispatchers.IO) {
            val res = try {
                http.newCall(request(path, method, body)).execute()
            } catch (e: Exception) {
                throw ApiError(0, e.message ?: "network error")
            }
            res.use {
                it.headers("Set-Cookie").forEach { c ->
                    val m = Regex("^fsa_session=([^;]*)").find(c)
                    if (m != null) {
                        val v = m.groupValues[1]
                        if (v.isEmpty()) prefs.clearSession() else prefs.sessionCookie = v
                    }
                }
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
                        prefs.clearSession()
                        HubSession.reportUnauthorized(HubTarget.Renzo)
                    }
                    val msg = try {
                        Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)
                    } catch (_: Exception) { null }
                    throw ApiError(it.code, msg ?: "HTTP ${it.code}")
                }
                text
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
