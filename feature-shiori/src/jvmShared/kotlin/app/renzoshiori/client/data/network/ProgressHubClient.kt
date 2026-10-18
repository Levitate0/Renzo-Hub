package app.renzoshiori.client.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.http.GET
import java.net.URLEncoder

/** GET /api/auth/image-token — the short-lived token SignalR connects with
 *  (WebSocket upgrades can't carry an Authorization header). */
interface ProgressTokenApi {
    @GET("api/auth/image-token")
    suspend fun imageToken(): ImageTokenDto
}

@Serializable
data class ImageTokenDto(val token: String = "", val expiresAt: String = "")

@Serializable
data class ProgressCardDto(
    val pageCount: Int = 0,
    val provider: String = "",
    val language: String = "",
    val scanlator: String? = null,
    val title: String = "",
    val url: String? = null,
    val chapterNumber: Double? = null,
    val chapterName: String = "",
    val thumbnailUrl: String? = null,
    val seriesId: String? = null,
)

/** SignalR "Progress" payload (backend ProgressState, camelCase, numeric enums). */
@Serializable
data class ProgressStateDto(
    val id: String = "",
    val jobType: Int = -1,
    val download: ProgressCardDto? = null,
    val progressStatus: Int = 0,
    val percentage: Double = 0.0,
    val message: String? = null,
    val errorMessage: String? = null,
)

/** One live entry of the downloads panel. */
data class ActiveDownload(
    val id: String,
    val title: String,
    val chapterName: String,
    val thumbnailUrl: String?,
    val percentage: Int,
    val provider: String = "",
    val seriesId: String? = null,
    /** Still starting (no page fetched yet) rather than downloading. */
    val starting: Boolean = false,
)

/**
 * A minimal SignalR client for the backend's ProgressHub — the same stream the
 * web's downloads panel consumes (download-status.tsx / useDownloadProgress). The
 * JSON hub protocol over a raw OkHttp WebSocket is small enough to speak
 * directly: connect to `/progress?access_token=…` (skip-negotiate, WebSockets
 * transport), send the `{"protocol":"json","version":1}` handshake, then read
 * 0x1E-delimited records; type 1 invocations of target "Progress" carry the
 * ProgressState. A `{"type":6}` ping goes out every 15s so the server's
 * client-timeout never trips.
 *
 * On every (re)connect the server first sends "ActiveDownloads", the full list
 * of what is downloading right now, and it replaces this client's list: a
 * download that ended while the socket was down used to stay "in progress"
 * here until the app restarted.
 */
class ProgressHubClient(
    private val serverUrl: String,
    private val fetchToken: suspend () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _downloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, ActiveDownload>> = _downloads.asStateFlow()

    /** Runs until cancelled: connect, stream, reconnect after 5s on any drop. */
    suspend fun run(): Unit = coroutineScope {
        val client = OkHttpClient.Builder()
            .pingInterval(java.time.Duration.ofSeconds(20))
            .build()
        try {
            while (isActive) {
                runCatching { connectOnce(client) }
                if (!isActive) break
                delay(5_000)
            }
        } finally {
            client.dispatcher.executorService.shutdown()
        }
    }

    private suspend fun connectOnce(client: OkHttpClient) = coroutineScope {
        val token = runCatching { fetchToken() }.getOrNull()
        val url = buildString {
            append(serverUrl.trimEnd('/').replaceFirst("http", "ws"))
            append("/progress")
            if (!token.isNullOrBlank()) {
                append("?access_token=").append(URLEncoder.encode(token, "UTF-8"))
            }
        }
        val closed = CompletableDeferred<Unit>()
        val ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"protocol":"json","version":1}""" + RS)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    text.split(RS).forEach { record ->
                        if (record.isNotBlank()) runCatching { handleRecord(record) }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.complete(Unit)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }
            },
        )
        val ping = launch {
            while (isActive) {
                delay(15_000)
                if (!ws.send("""{"type":6}""" + RS)) break
            }
        }
        try {
            closed.await()
        } finally {
            ping.cancel()
            ws.cancel()
        }
    }

    private fun handleRecord(record: String) {
        val obj = json.parseToJsonElement(record).jsonObject
        if (obj["type"]?.jsonPrimitive?.intOrNull != 1) return
        val arg = obj["arguments"]?.jsonArray?.firstOrNull() ?: return
        // SignalR matches method names case-insensitively; so does this.
        when (obj["target"]?.jsonPrimitive?.content?.lowercase()) {
            "progress" -> onProgress(json.decodeFromJsonElement(ProgressStateDto.serializer(), arg))
            "activedownloads" -> onSnapshot(
                arg.jsonArray.mapNotNull {
                    runCatching { json.decodeFromJsonElement(ProgressStateDto.serializer(), it) }.getOrNull()
                },
            )
        }
    }

    /** The server's list of what is downloading now — replaces ours. */
    private fun onSnapshot(states: List<ProgressStateDto>) {
        _downloads.value = states
            .filter {
                it.jobType == JOB_TYPE_DOWNLOAD && it.download != null &&
                    it.progressStatus != STATUS_COMPLETED && it.progressStatus != STATUS_FAILED
            }
            .associate { it.id to it.toActive(it.download!!) }
    }

    private fun ProgressStateDto.toActive(card: ProgressCardDto) = ActiveDownload(
        id = id,
        title = card.title,
        chapterName = card.chapterName,
        thumbnailUrl = card.thumbnailUrl,
        percentage = percentage.let { if (it.isFinite()) it else 0.0 }.coerceIn(0.0, 100.0).toInt(),
        provider = card.provider,
        seriesId = card.seriesId,
        starting = progressStatus == STATUS_STARTED,
    )

    /** Mirror of the web's useDownloadProgress reducer. */
    private fun onProgress(p: ProgressStateDto) {
        if (p.jobType != JOB_TYPE_DOWNLOAD) return
        _downloads.update { prev ->
            // Completed or failed downloads leave the visual stack.
            if (p.progressStatus == STATUS_COMPLETED || p.progressStatus == STATUS_FAILED) {
                return@update prev - p.id
            }
            val existing = prev[p.id]
            val card = p.download
            // No card info yet (progress event raced the payload): skip.
            val title = existing?.title ?: card?.title ?: return@update prev
            prev + (p.id to ActiveDownload(
                id = p.id,
                title = title,
                chapterName = existing?.chapterName ?: card?.chapterName.orEmpty(),
                thumbnailUrl = existing?.thumbnailUrl ?: card?.thumbnailUrl,
                percentage = p.percentage.let { if (it.isFinite()) it else 0.0 }
                    .coerceIn(0.0, 100.0).toInt(),
                provider = existing?.provider ?: card?.provider.orEmpty(),
                seriesId = existing?.seriesId ?: card?.seriesId,
                starting = p.progressStatus == STATUS_STARTED,
            ))
        }
    }

    private companion object {
        /** SignalR record separator. */
        const val RS = "\u001e"
        const val JOB_TYPE_DOWNLOAD = 6
        const val STATUS_STARTED = 0
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
    }
}
