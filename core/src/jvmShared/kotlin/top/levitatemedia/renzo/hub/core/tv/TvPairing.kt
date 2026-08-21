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

sealed interface TvCodeResult {
    /** The server issued a code — show it and start polling. */
    data class Granted(val code: TvCode) : TvCodeResult

    /**
     * The server predates pairing (404) or is unreachable — callers hide the
     * option (fall back to the password form) rather than showing a broken one.
     * Any unexpected non-2xx lands here too, to stay conservative.
     */
    data object Unsupported : TvCodeResult

    /**
     * 503: the server's live-pairing table is full. Transient and
     * self-clearing — keep the option visible, show [message], offer a retry.
     * Collapsing this into [Unsupported] presented a busy minute as a
     * permanently missing feature.
     */
    data class Busy(val message: String) : TvCodeResult
}

sealed interface TvPollState {
    /** Not approved yet; keep waiting. */
    data object Pending : TvPollState

    /**
     * Approved. [body] is the raw response, parsed by the half that asked —
     * the two servers issue different credentials and :core deliberately does
     * not know which. [setCookies] carries the response's Set-Cookie headers
     * verbatim: the anime half's credential is an fsa_session cookie on the
     * approval response, not a token in the body.
     */
    data class Approved(val body: String, val setCookies: List<String> = emptyList()) : TvPollState

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
     * The three outcomes are deliberately distinct ([TvCodeResult]): a 503 —
     * the server's pending-pairing table is full — must NOT read as "this
     * server has no pairing support", because it clears itself in a minute.
     */
    suspend fun requestCode(deviceName: String): TvCodeResult = withContext(Dispatchers.IO) {
        val body = json.encodeToString(DeviceNameBody(deviceName))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url("/api/auth/tv/code"))
            .post(body)
            .build()
        runCatching {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                when {
                    res.isSuccessful ->
                        runCatching { json.decodeFromString<TvCode>(text) }.getOrNull()
                            ?.let { TvCodeResult.Granted(it) }
                            ?: TvCodeResult.Unsupported
                    res.code == 503 -> {
                        // The wording is a server concern and will drift —
                        // read it from the body, never hardcode it.
                        val msg = runCatching { json.decodeFromString<ErrorBody>(text).error }
                            .getOrNull()?.takeIf { it.isNotBlank() }
                            ?: "Too many devices are pairing right now. Try again in a minute."
                        TvCodeResult.Busy(msg)
                    }
                    // 429: THIS caller asked too often (per-address rate
                    // limit) — transient like 503, NOT missing support.
                    // Collapsing it into Unsupported made the feature vanish
                    // for a user who tapped "pair a TV" a few times too many
                    // (HANDOFFrenzohub_tvcode.md §2).
                    res.code == 429 -> {
                        val msg = runCatching { json.decodeFromString<ErrorBody>(text).error }
                            .getOrNull()?.takeIf { it.isNotBlank() }
                            ?: "Too many code requests from this device. Try again in a few minutes."
                        TvCodeResult.Busy(msg)
                    }
                    else -> TvCodeResult.Unsupported
                }
            }
        }.getOrElse { TvCodeResult.Unsupported }
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
                                "approved" -> TvPollState.Approved(text, res.headers("Set-Cookie"))
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
    @Serializable private data class ErrorBody(val error: String = "")
    @Serializable private data class DeviceCodeBody(val deviceCode: String)
    @Serializable private data class PollStatus(val status: String = "pending")
}
