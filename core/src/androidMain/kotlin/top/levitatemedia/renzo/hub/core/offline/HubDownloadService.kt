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
    private lateinit var engine: DownloadEngine

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private var worker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = OfflineStore(applicationContext)
        queue = DownloadQueue()
        // The engine is the shared, platform-free downloader (DownloadEngine
        // in jvmShared) — this Service adds only what Android requires of it:
        // the foreground notification and the metered-network gate.
        engine = DownloadEngine(
            store = store,
            queue = queue,
            isBlocked = ::blockedByMeteredNetwork,
            onStatus = { text, done, total -> updateNotification(text, done, total) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            queue.clear()
            engine.stop()
            worker?.cancel()
            finish()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForeground(NOTIF_ID, notification("Preparing download…", 0, 0))
        if (!engine.isRunning) {
            worker = scope.launch {
                try {
                    engine.runQueue()
                } finally {
                    finish()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
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
