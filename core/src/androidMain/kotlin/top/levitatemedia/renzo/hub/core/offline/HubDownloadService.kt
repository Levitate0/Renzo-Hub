package top.levitatemedia.renzo.hub.core.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Hub's one offline downloader, for both halves.
 *
 * Foreground so it outlives the UI. Replaces the Shiori client's
 * RenzoDownloadService, with three changes that the anime half forces:
 *
 *  1. **Auth comes from [HubSession] per request**, not from a token baked into
 *     the job. A long queue outlived the old baked token; and the Renzo half
 *     authenticates with a cookie, not a Bearer, which the service never needs
 *     to know.
 *  2. **Bodies stream to disk** with `Range` resume. The old fetch did
 *     `inputStream.readBytes()` — fine for a page image, an OOM for a 2 GB
 *     episode, and a total restart if the connection dropped at 99%.
 *  3. **Assets are resolved per item, just before fetching** (see
 *     [DownloadSource]), because debrid URLs expire.
 */
class HubDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "renzo_hub_downloads"
        const val NOTIF_ID = 4711
        const val ACTION_ENQUEUE = "enqueue"
        const val ACTION_STOP = "stop"

        /**
         * Concurrent fetches for small assets. Pages are latency-bound, so
         * parallelism is a real win. Video is forced serial regardless — two
         * simultaneous multi-gigabyte writes only thrash the disk.
         */
        const val SMALL_ASSET_CONCURRENCY = 5

        /** Attempts per video, each re-resolving the link first. */
        const val VIDEO_ATTEMPTS = 4
        const val RETRY_BACKOFF_MS = 3_000L

        /** Headroom kept free so a download never fills the device completely. */
        const val SPACE_MARGIN_BYTES = 256L * 1024 * 1024

        /** Downloads wait for unmetered network unless this is turned off. */
        const val KEY_WIFI_ONLY = "downloads.wifiOnly"

        fun start(context: Context) {
            val intent = Intent(context, HubDownloadService::class.java).setAction(ACTION_ENQUEUE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HubDownloadService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    private lateinit var store: OfflineStore
    private lateinit var queue: DownloadQueue

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private var worker: Job? = null
    private val running = AtomicBoolean(false)

    /** SAF writes are not safe to interleave; serialise them. */
    private val writeLock = Mutex()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // No read timeout: a large episode body legitimately takes minutes.
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = OfflineStore(applicationContext)
        queue = DownloadQueue(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            queue.clear()
            running.set(false)
            worker?.cancel()
            finish()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForeground(NOTIF_ID, notification("Preparing download…", 0, 0))
        if (running.compareAndSet(false, true)) {
            worker = scope.launch { runQueue() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun runQueue() {
        try {
            while (running.get()) {
                val job = queue.take() ?: break
                // Wi-Fi-only is on by default: a 3 GB episode over cellular is
                // not something to do by accident. The job goes back on the
                // queue untouched and resumes the next time downloads start.
                if (blockedByMeteredNetwork()) {
                    queue.pushFront(job)
                    break
                }
                runCatching { runJob(job) }
            }
        } finally {
            running.set(false)
            DownloadBus.idle()
            finish()
        }
    }

    /** True when downloads should wait for an unmetered connection. */
    private fun blockedByMeteredNetwork(): Boolean {
        if (store.kvGet(KEY_WIFI_ONLY) == "off") return false
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        // NOT_METERED covers Wi-Fi and Ethernet without hardcoding transports,
        // and respects a user who has flagged their home Wi-Fi as metered.
        return !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── one job ──────────────────────────────────────────────────────────────

    private suspend fun runJob(job: DownloadJob) {
        val source = DownloadSources.of(job.target) ?: StaticDownloadSource
        // Best-effort warm-up; a failure here just means the per-item resolves
        // do the work themselves.
        runCatching { source.prepare(job) }
        ensureParent(job)

        val total = job.items.size
        job.items.forEachIndexed { i, item ->
            if (!running.get()) return

            DownloadBus.emit(
                DownloadProgress(
                    active = true,
                    target = job.target,
                    parentId = job.parentId,
                    parentTitle = job.parentTitle,
                    itemKey = item.itemKey,
                    done = i,
                    total = total,
                ),
            )
            updateNotification("${job.parentTitle} · ${i + 1}/$total", i, total)

            val key = DownloadManifest.itemKey(job.target, item.itemKey)
            val existing = store.getManifest().items[key]
            if (existing?.complete != true) {
                // One bad item must not abort the rest of the batch.
                runCatching { downloadItem(job, item, source, i, total) }
            }
            DownloadBus.itemFinished()
        }
    }

    private suspend fun downloadItem(
        job: DownloadJob,
        item: PendingItem,
        source: DownloadSource,
        itemIndex: Int,
        itemTotal: Int,
    ) {
        val pending = source.resolveAssets(job, item)
        if (pending.isEmpty()) return

        val saved = arrayOfNulls<OfflineAsset>(pending.size)
        val sizes = LongArray(pending.size)

        val big = pending.filter { it.role == AssetRole.VIDEO }
        val small = pending.filter { it.role != AssetRole.VIDEO }

        // Small assets in parallel; the network, not the disk, is the limit.
        small.chunked(SMALL_ASSET_CONCURRENCY).forEach { batch ->
            if (!running.get()) return
            batch.map { asset ->
                scope.async {
                    val idx = pending.indexOf(asset)
                    val rel = relPathFor(job, item, asset)
                    val out = fetch(job.target, job.baseUrl, asset.url, rel, resumable = false, onProgress = null)
                    if (out.complete) {
                        saved[idx] = OfflineAsset(asset.role, asset.index, rel, label = asset.label)
                        sizes[idx] = out.bytes
                    }
                }
            }.awaitAll()
        }

        // Video serially, with resume, re-resolution and live progress.
        big.forEach { asset ->
            if (!running.get()) return
            val idx = pending.indexOf(asset)
            val rel = relPathFor(job, item, asset)
            val n = fetchVideoWithRetry(job, item, source, asset, rel) { got, total ->
                if (total > 0) {
                    DownloadBus.emit(
                        DownloadProgress(
                            active = true,
                            target = job.target,
                            parentId = job.parentId,
                            parentTitle = job.parentTitle,
                            itemKey = item.itemKey,
                            done = itemIndex,
                            total = itemTotal,
                            itemFraction = (got.toFloat() / total).coerceIn(0f, 1f),
                        ),
                    )
                }
            }
            if (n >= 0) {
                saved[idx] = OfflineAsset(asset.role, asset.index, rel, label = asset.label)
                sizes[idx] = n
            }
        }

        val assets = saved.filterNotNull()

        // Only the CONTENT has to arrive. Losing a subtitle track or a cover is
        // a cosmetic loss; treating it as fatal would delete an already-fetched
        // 2 GB episode because one .vtt 404'd — which is exactly what a missing
        // sidecar used to do.
        val requiredMissing = pending.withIndex().any { (i, asset) ->
            asset.role in setOf(AssetRole.VIDEO, AssetRole.PAGE) && saved[i] == null
        }
        // A partial item is worse than none: the player would open a truncated
        // file and the reader would show a chapter with holes in it.
        if (requiredMissing) {
            assets.forEach { runCatching { store.deletePath(it.relPath) } }
            return
        }

        val entry = OfflineItem(
            target = job.target,
            parentId = job.parentId,
            itemKey = item.itemKey,
            ordinal = item.ordinal,
            title = item.title.ifBlank { job.parentTitle },
            assets = assets.sortedWith(compareBy({ it.role.ordinal }, { it.index })),
            bytes = sizes.sum(),
            savedAt = System.currentTimeMillis(),
            complete = true,
        )
        writeLock.withLock {
            store.updateManifest { m ->
                m.copy(items = m.items + (DownloadManifest.itemKey(job.target, item.itemKey) to entry))
            }
        }
    }

    private suspend fun ensureParent(job: DownloadJob) {
        val key = DownloadManifest.parentKey(job.target, job.parentId)
        val existing = store.getManifest().parents[key]
        if (existing?.coverPath != null) return

        var coverPath: String? = existing?.coverPath
        if (coverPath == null && !job.coverUrl.isNullOrBlank()) {
            val rel = "${nsFor(job.target)}/covers/${sanitize(job.parentId)}"
            if (fetch(job.target, job.baseUrl, job.coverUrl, rel, resumable = false, onProgress = null).complete) {
                coverPath = rel
            }
        }
        val parent = OfflineParent(
            target = job.target,
            parentId = job.parentId,
            title = job.parentTitle,
            coverPath = coverPath,
            description = job.parentDescription,
            author = job.parentAuthor,
        )
        writeLock.withLock {
            store.updateManifest { m -> m.copy(parents = m.parents + (key to parent)) }
        }
    }

    // ── fetching ─────────────────────────────────────────────────────────────

    /**
     * Streams one asset to [relPath]. Returns bytes on disk, or -1 on failure.
     *
     * With [resumable], an existing partial file is continued via a `Range`
     * request. A server that ignores Range answers 200 rather than 206, and the
     * file is rewritten from zero — checked explicitly, because appending to a
     * partial file after a 200 would silently corrupt it.
     */
    /**
     * Fetch a video, re-resolving the link between attempts.
     *
     * Debrid links are short-lived AND IP-bound, so a resume against the URL we
     * started with will 404 even though the provider supports Range perfectly
     * well. Every retry therefore asks the source for a fresh link before
     * continuing from the bytes already on disk.
     *
     * A fresh link can point at a DIFFERENT release. [FetchOutcome.mismatch]
     * catches that by size before anything is written; the partial is thrown
     * away and the next attempt starts clean, because appending one release
     * onto another produces a file that looks whole and plays as garbage.
     */
    private suspend fun fetchVideoWithRetry(
        job: DownloadJob,
        item: PendingItem,
        source: DownloadSource,
        asset: PendingAsset,
        relPath: String,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        var url = asset.url

        // A partial file is worthless unless we know WHICH release produced it.
        // relPath is keyed by title/episode only, so a `.part` left by a killed
        // process could otherwise be resumed against a freshly-resolved link for
        // a different release: bytes of A followed by bytes of B, ending at the
        // expected length, indistinguishable from a complete episode.
        //
        // The recorded size is that identity. No record and a partial on disk
        // means the identity is unknown — throw the partial away rather than
        // guess.
        var expectTotal = store.kvGet(partialKey(relPath))?.toLongOrNull() ?: -1L
        if (expectTotal <= 0 && store.sizeOf(relPath) > 0) {
            runCatching { store.deletePath(relPath) }
        }

        repeat(VIDEO_ATTEMPTS) { attempt ->
            if (!running.get()) return -1

            val out = fetch(
                job.target, job.baseUrl, url, relPath,
                resumable = true, expectTotal = expectTotal, onProgress = onProgress,
            )

            if (out.mismatch) {
                // Different file behind the new link — start over rather than
                // splice two releases together.
                runCatching { store.deletePath(relPath) }
                expectTotal = out.total
                store.kvSet(partialKey(relPath), out.total.toString())
                return@repeat
            }
            if (out.total > 0) {
                if (expectTotal != out.total) store.kvSet(partialKey(relPath), out.total.toString())
                expectTotal = out.total
                // Refuse to fill the device. Checked here rather than at
                // enqueue because the size is not known until the server
                // answers.
                val remaining = out.total - out.bytes
                if (remaining > 0 && store.usableSpaceBytes() in 1 until (remaining + SPACE_MARGIN_BYTES)) {
                    runCatching { store.deletePath(relPath) }
                    return -1
                }
            }
            if (out.complete) {
                store.kvSet(partialKey(relPath), "")
                return out.bytes
            }
            if (!running.get() || attempt == VIDEO_ATTEMPTS - 1) return -1

            // The link is almost certainly stale by now; get a new one.
            val fresh = runCatching { source.resolveAssets(job, item) }.getOrNull()
                ?.firstOrNull { it.role == AssetRole.VIDEO }
                ?: return -1
            url = fresh.url
            kotlinx.coroutines.delay(RETRY_BACKOFF_MS * (attempt + 1))
        }
        return -1
    }

    /** Outcome of one transfer attempt. */
    private data class FetchOutcome(
        val bytes: Long,
        /** Reached the end of the body — not merely "wrote something". */
        val complete: Boolean,
        /** Full size of the resource, or -1 when the server didn't say. */
        val total: Long = -1L,
        /** The resource is a different size than last time: a DIFFERENT file. */
        val mismatch: Boolean = false,
    )

    /**
     * Streams one asset to [relPath].
     *
     * With [resumable], an existing partial file is continued via a `Range`
     * request. A server that ignores Range answers 200 rather than 206, and the
     * file is rewritten from zero — checked explicitly, because appending after
     * a 200 would silently corrupt it.
     *
     * [expectTotal] guards the retry path: a re-resolved debrid link can point
     * at a different release, and appending to a partial of a different file
     * would produce a corrupt episode that still looks complete. On a size
     * mismatch nothing is written.
     */
    private fun fetch(
        target: HubTarget,
        baseUrl: String,
        url: String,
        relPath: String,
        resumable: Boolean,
        expectTotal: Long = -1L,
        onProgress: ((Long, Long) -> Unit)?,
    ): FetchOutcome {
        val absolute = if (url.startsWith("http", ignoreCase = true)) url else baseUrl.trimEnd('/') + url
        val already = if (resumable) store.sizeOf(relPath) else 0L

        val builder = Request.Builder().url(absolute).get()
        // Credentials go to the USER'S SERVER and nowhere else. An episode's
        // stream URL is frequently an absolute link to a third-party debrid CDN,
        // and signing that unconditionally would hand a session cookie (or a
        // Bearer token) to a host that has no business seeing it. The player has
        // always scoped this by host; the downloader must match.
        if (sameHost(absolute, baseUrl)) HubSession.signFor(target, builder)
        if (already > 0) builder.header("Range", "bytes=$already-")

        return try {
            http.newCall(builder.build()).execute().use { res ->
                if (!res.isSuccessful) return FetchOutcome(0, false)
                // Branch on the STATUS, never on Accept-Ranges: AllDebrid omits
                // that header on a 206, so reading it back would wrongly
                // conclude the provider can't resume.
                val resumed = res.code == 206 && already > 0
                val body = res.body ?: return FetchOutcome(0, false)

                // Content-Range carries the true total; Content-Length on a 206
                // is only the remaining bytes.
                val total = res.header("Content-Range")
                    ?.substringAfter('/', "")
                    ?.toLongOrNull()
                    ?: body.contentLength().let {
                        if (it > 0) it + (if (resumed) already else 0L) else -1L
                    }

                if (expectTotal > 0 && total > 0 && total != expectTotal) {
                    return FetchOutcome(0, false, total, mismatch = true)
                }

                val out = store.openOutput(relPath, append = resumed)
                    ?: return FetchOutcome(0, false, total)
                // Append writes wherever the file ends when the stream is
                // opened — not necessarily the offset this Range was asked for.
                // If anything truncated or removed the partial in between, the
                // body would splice in at the wrong place and still finish at
                // the right length. Bail instead.
                if (resumed && store.sizeOf(relPath) != already) {
                    runCatching { out.close() }
                    return FetchOutcome(0, false, total)
                }
                var written = if (resumed) already else 0L
                var reachedEnd = false
                out.use { sink ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (!running.get()) return FetchOutcome(written, false, total)
                            val n = input.read(buf)
                            if (n <= 0) { reachedEnd = true; break }
                            sink.write(buf, 0, n)
                            written += n
                            onProgress?.invoke(written, total)
                        }
                    }
                }
                // "Complete" means the stream ended AND, where the size is
                // known, we have all of it. Without the second half a truncated
                // body that closes cleanly would be recorded as a whole episode.
                val complete = reachedEnd && (total <= 0 || written >= total)
                FetchOutcome(written, complete, total)
            }
        } catch (_: Exception) {
            FetchOutcome(store.sizeOf(relPath), false)
        }
    }

    /**
     * True only when [url] is on the same host as the user's own server.
     * Anything unparseable is treated as foreign — failing closed here means a
     * download without credentials, which is recoverable; failing open would
     * leak the session.
     */
    private fun sameHost(url: String, baseUrl: String): Boolean = runCatching {
        val a = url.toHttpUrlOrNull() ?: return false
        val b = (if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl").toHttpUrlOrNull()
            ?: return false
        a.host.equals(b.host, ignoreCase = true)
    }.getOrDefault(false)

    /** Records the expected total of an in-flight partial — its release identity. */
    private fun partialKey(relPath: String) = "partial.total.$relPath"

    private fun nsFor(target: HubTarget) = when (target) {
        HubTarget.Renzo -> "renzo"
        HubTarget.Shiori -> "shiori"
    }

    private fun relPathFor(job: DownloadJob, item: PendingItem, asset: PendingAsset): String {
        val dir = "${nsFor(job.target)}/${sanitize(item.itemKey)}"
        val idx = asset.index.toString().padStart(4, '0')
        return when (asset.role) {
            AssetRole.VIDEO -> "$dir/video"
            AssetRole.SUBTITLE -> "$dir/sub_$idx.vtt"
            AssetRole.COVER -> "$dir/cover"
            AssetRole.PAGE -> "$dir/$idx"
        }
    }

    private fun sanitize(key: String): String = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    // ── notification ─────────────────────────────────────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Offline downloads", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    private fun updateNotification(text: String, done: Int, total: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification(text, done, total))
    }

    private fun notification(text: String, done: Int, total: Int): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Renzo Hub")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (total > 0) b.setProgress(total, done, false)
        return b.build()
    }
}
