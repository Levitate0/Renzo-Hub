package top.levitatemedia.renzo.hub.notify

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.notify.ShioriReleaseCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.notify.ReleaseAlerts
import top.levitatemedia.renzo.hub.core.notify.ReleaseNotice
import top.levitatemedia.renzo.tv.Prefs
import top.levitatemedia.renzo.tv.api.ApiClient
import top.levitatemedia.renzo.tv.notify.RenzoReleaseCheck
import java.util.concurrent.TimeUnit

/**
 * The background new-release check: both halves, every half hour or so, then
 * the notifications. Each half is checked on its own, so one server being
 * down costs the other nothing.
 */
class ReleaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // One check at a time: two at once would both see the same chapter
        // above the mark before either raised it, and announce it twice.
        checking.withLock {
            val notices = mutableListOf<ReleaseNotice>()
            attempt("Shiori") { notices += ShioriReleaseCheck.run(ShioriRuntime.app) }
            attempt("Renzo") {
                val prefs = Prefs()
                notices += RenzoReleaseCheck.run(prefs, ApiClient(prefs))
            }
            ReleaseNotifications.post(applicationContext, notices)
        }
        return Result.success()
    }

    private companion object {
        val checking = Mutex()
    }

    private suspend fun attempt(half: String, check: suspend () -> Unit) {
        try {
            check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next run tries again; nothing was marked as seen.
            Log.w("ReleaseWorker", "$half release check failed: ${e.message}")
        }
    }
}

/** Keeps WorkManager in step with what is switched on. */
object ReleaseSchedule {
    private const val PERIODIC = "release-check"

    fun sync(context: Context) {
        runCatching {
            val work = WorkManager.getInstance(context)
            if (HubTarget.entries.none { ReleaseAlerts.anyOn(it) }) {
                work.cancelUniqueWork(PERIODIC)
                return
            }
            // A half that has just been switched on has to take its baseline
            // now, not at the next run up to half an hour away: a chapter
            // released in between would be folded into the baseline and never
            // announced. Re-enqueuing the periodic check does that, because
            // its first run is immediate.
            //
            // Not a separate one-off run beside it. The two ran at the same
            // moment, and WorkManager lost the periodic job's next slot when
            // they finished together (seen on an Android 14 emulator): the
            // check simply stopped until the app was next started.
            val baseline = HubTarget.entries.any { ReleaseAlerts.anyOn(it) && !ReleaseAlerts.primed(it) }
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            work.enqueueUniquePeriodicWork(
                PERIODIC,
                if (baseline) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReleaseWorker>(30, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }.onFailure { Log.w("ReleaseSchedule", "Couldn't schedule the release check", it) }
    }
}

/** [ReleaseAlerts.Platform] for phones and tablets. */
class AndroidReleasePlatform(private val app: Context) : ReleaseAlerts.Platform {
    override fun permitted(): Boolean = ReleaseNotifications.canPost(app)

    override fun requestPermission() {
        // Android 13+ asks at runtime, through the activity. Otherwise (older
        // Android, or the permission granted but the app's notifications
        // switched off) a block can only be lifted in settings.
        val ask = permissionAsker
        val ungranted = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (ungranted && ask != null) ask() else openSettings()
    }

    override fun sync() = ReleaseSchedule.sync(app)

    @SuppressLint("InlinedApi")
    override fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
        }
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        /** Set by the activity while it is alive: the runtime permission needs one. */
        @Volatile
        var permissionAsker: (() -> Unit)? = null
    }
}
