package top.levitatemedia.renzo.hub.core.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TV sign-in by pairing code, for both halves of the Hub.
 *
 * Typing a password on a TV remote is miserable, and for the users who most
 * need this — someone whose only screen IS the television — there is no phone
 * to fall back to. So the TV shows a short code, the user approves it from any
 * browser on a device where typing is bearable, and the TV is signed in.
 *
 * This is the OAuth device-authorisation shape, deliberately: it is well
 * understood, and the security properties are known. The TV holds a secret
 * [TvCode.deviceCode] it never displays, and displays a short
 * [TvCode.userCode] it never uses for authentication. Only the pairing request
 * knows both.
 *
 * The credential that comes back is whatever that half normally issues — a JWT
 * for the manga side, an opaque session cookie for the anime side — so nothing
 * downstream has to know the TV was involved. Pairing replaces the typing, not
 * the session model.
 *
 * Both servers must implement the same three endpoints; see
 * docs/TV-PAIRING-*.md. A server that has not shipped them answers 404, and the
 * caller is expected to hide the option rather than fail.
 */
@Serializable
data class TvCode(
    /** Shown on the TV. Short, human-transcribable, useless on its own. */
    val userCode: String,
    /** Secret. Held by the TV, sent only when polling. Never displayed. */
    val deviceCode: String,
    /** Where the user goes to approve — served by the instance itself. */
    val verificationUrl: String,
    /** Seconds until [userCode] stops being accepted. */
    val expiresIn: Int = 600,
    /** Seconds the server wants between polls. */
    val interval: Int = 5,
)

sealed interface TvPollState {
    /** Not approved yet; keep waiting. */
    data object Pending : TvPollState

    /**
     * Approved. [body] is the raw response, parsed by the half that asked —
     * the two servers issue different credentials and :core deliberately does
     * not know which.
     */
    data class Approved(val body: String) : TvPollState

    /** The user rejected it, or the code expired. Start over. */
    data class Failed(val reason: String) : TvPollState
}

class TvPairingClient(private val baseUrl: String) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Ask for a pairing code. [deviceName] is what the user will see in their
     * account's device list, so it should say which television this is.
     *
     * Returns null when the server has no pairing support (404) or is
     * unreachable — callers hide the option rather than showing a broken one.
     */
    suspend fun requestCode(deviceName: String): TvCode? = withContext(Dispatchers.IO) {
        val body = json.encodeToString(DeviceNameBody(deviceName))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url("/api/auth/tv/code"))
            .post(body)
            .build()
        runCatching {
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@use null
                res.body?.string()?.let { json.decodeFromString<TvCode>(it) }
            }
        }.getOrNull()
    }

    /**
     * Poll until the code is approved, rejected or expires.
     *
     * Honours the server's [TvCode.interval] and gives up at
     * [TvCode.expiresIn] rather than polling a dead code forever. A transient
     * network failure is treated as "pending" — a TV on flaky wifi should not
     * lose a pairing the user already approved.
     */
    suspend fun awaitApproval(
        code: TvCode,
        onTick: (secondsLeft: Int) -> Unit = {},
    ): TvPollState = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        val intervalMs = code.interval.coerceAtLeast(1) * 1000L

        while (System.currentTimeMillis() < deadline) {
            val remaining = ((deadline - System.currentTimeMillis()) / 1000).toInt()
            onTick(remaining.coerceAtLeast(0))

            val body = json.encodeToString(DeviceCodeBody(code.deviceCode))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url("/api/auth/tv/poll"))
                .post(body)
                .build()

            val outcome = runCatching {
                http.newCall(request).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    when {
                        // 428 is the device-flow convention for "still waiting".
                        res.code == 428 -> TvPollState.Pending
                        res.isSuccessful -> {
                            val status = json.decodeFromString<PollStatus>(text).status
                            when (status) {
                                "approved" -> TvPollState.Approved(text)
                                "denied" -> TvPollState.Failed("The sign-in request was denied.")
                                "expired" -> TvPollState.Failed("The code expired. Try again.")
                                else -> TvPollState.Pending
                            }
                        }
                        res.code == 404 || res.code == 410 ->
                            TvPollState.Failed("The code expired. Try again.")
                        else -> TvPollState.Pending
                    }
                }
            }.getOrElse {
                // Offline for a moment: keep waiting rather than throwing away
                // an approval the user may already have granted.
                TvPollState.Pending
            }

            if (outcome !is TvPollState.Pending) return@withContext outcome
            delay(intervalMs)
        }
        TvPollState.Failed("The code expired. Try again.")
    }

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    @Serializable private data class DeviceNameBody(val deviceName: String)
    @Serializable private data class DeviceCodeBody(val deviceCode: String)
    @Serializable private data class PollStatus(val status: String = "pending")
}
