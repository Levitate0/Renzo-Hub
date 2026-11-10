package top.levitatemedia.renzo.hub.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.renzoshiori.client.data.model.SourceHealthDto
import top.levitatemedia.renzo.hub.HubActivity
import top.levitatemedia.renzo.hub.R

/**
 * "A source isn't working": one notification per dead source (see
 * ShioriSourceAlertCheck), at most once a day while it stays dead. A source
 * that recovers is forgotten, so dying again notifies again.
 */
object SourceAlertNotifications {
    private const val CHANNEL = "source_problems"
    private const val TAG = "source-alert"
    private const val PREFS = "hub_source_alerts"
    private const val REPEAT_MS = 24L * 3600_000

    fun post(context: Context, dead: List<SourceHealthDto>, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val names = dead.map { it.provider }.toSet()
        prefs.edit().apply { prefs.all.keys.filter { it !in names }.forEach { remove(it) } }.apply()
        if (dead.isEmpty() || !ReleaseNotifications.canPost(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        for (s in dead) {
            if (nowMs - prefs.getLong(s.provider, 0L) < REPEAT_MS) continue
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_hub)
                .setContentTitle("${s.provider} isn't working")
                .setContentText("It has failed on your series all day. Open Sources › Health to migrate them.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "${s.provider} has failed on ${s.enabledCount} of your series all day without serving a chapter. " +
                        "Other sources still cover them where they can. Open Sources › Health to migrate them.",
                ))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, s.provider.hashCode()))
            try {
                manager.notify(TAG, s.provider.hashCode(), builder.build())
                prefs.edit().putLong(s.provider, nowMs).apply()
            } catch (_: SecurityException) {
                // The permission was revoked between the check and the post.
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Source problems", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A source your Shiori series use has stopped working."
            },
        )
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
