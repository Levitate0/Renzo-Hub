package top.levitatemedia.renzo.hub.notify

import android.Manifest
import android.app.ActivityManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
import app.renzoshiori.client.data.notify.ShioriSourceAlertCheck
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
 * the notifications. Also the dead-source alert (SourceAlertNotifications). Each half is checked on its own, so one server being
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
            attempt("Sources") {
                SourceAlertNotifications.post(applicationContext, ShioriSourceAlertCheck.run(ShioriRuntime.app))
            }
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

    override fun backgroundLimited(): Boolean {
        // Battery optimisation is on for every app by default. Then Doze holds
        // the half-hourly check while the phone sleeps, and app standby spaces
        // it out further the less the app is opened, so new releases showed
        // up only around the times the app was open (reported 2026-10-25).
        val power = app.getSystemService(PowerManager::class.java)
        val optimised = power?.isIgnoringBatteryOptimizations(app.packageName) == false
        return optimised || backgroundRestricted()
    }

    /** The app's battery use set to Restricted: background work stops altogether. */
    private fun backgroundRestricted(): Boolean =
        Build.VERSION.SDK_INT >= 28 && app.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    @SuppressLint("BatteryLife")
    override fun allowBackground() {
        val pkg = Uri.parse("package:${app.packageName}")
        // Restricted can only be lifted on the app's own settings page
        // (App battery usage → Unrestricted). Otherwise Android has a direct
        // "let it run in the background?" prompt.
        val intent = if (backgroundRestricted()) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        }
        val shown = runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!shown) {
            // Some phones drop the direct prompt; the list it belongs to always exists.
            runCatching {
                app.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    override val hasSleepingAppsList: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

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
