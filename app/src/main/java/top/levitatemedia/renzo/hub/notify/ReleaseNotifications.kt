package top.levitatemedia.renzo.hub.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.withTimeoutOrNull
import top.levitatemedia.renzo.hub.HubActivity
import top.levitatemedia.renzo.hub.R
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.notify.ReleaseNotice
import top.levitatemedia.renzo.hub.core.notify.describe

/**
 * Posts the new-release notifications: one per series, grouped per half.
 *
 * A series that is still showing a notice from an earlier check gets the new
 * numbers added to it ("Chapters 51–52") rather than replaced, so a release
 * nobody has looked at yet is not lost to the next one.
 */
object ReleaseNotifications {
    const val ACTION_OPEN = "top.levitatemedia.renzo.hub.OPEN_SERIES"
    const val EXTRA_TARGET = "hub.open.target"
    const val EXTRA_ID = "hub.open.id"

    private const val CHANNEL_CHAPTERS = "release_chapters"
    private const val CHANNEL_EPISODES = "release_episodes"
    private const val TAG = "release"
    private const val TAG_SUMMARY = "release-summary"
    private const val EXTRA_NUMBERS = "hub.release.numbers"

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    suspend fun post(context: Context, notices: List<ReleaseNotice>) {
        if (notices.isEmpty() || !canPost(context)) return
        ensureChannels(context)
        val system = context.getSystemService(NotificationManager::class.java)
        val manager = NotificationManagerCompat.from(context)
        val showing = runCatching { system.activeNotifications.toList() }.getOrDefault(emptyList())

        for (notice in notices) {
            val id = idOf(notice.target, notice.id)
            val earlier = showing.firstOrNull { it.id == id && it.tag == TAG }
                ?.notification?.extras?.getDoubleArray(EXTRA_NUMBERS)?.toList().orEmpty()
            val numbers = (earlier + notice.numbers).distinct().sorted()
            val merged = notice.copy(numbers = numbers, name = if (numbers.size == 1) notice.name else null)
            val builder = NotificationCompat.Builder(context, channelOf(notice.target))
                .setSmallIcon(R.drawable.ic_stat_hub)
                .setContentTitle(notice.title)
                .setContentText(merged.describe())
                .setAutoCancel(true)
                .setGroup(groupOf(notice.target))
                .setContentIntent(openIntent(context, notice, id))
                .setShowWhen(true)
                .addExtras(Bundle().apply { putDoubleArray(EXTRA_NUMBERS, numbers.toDoubleArray()) })
            cover(context, notice.coverUrl)?.let { builder.setLargeIcon(it) }
            notify(manager, context, TAG, id, builder)
        }

        // A summary per half once it has more than one, so they bundle into a
        // single expandable entry instead of a wall.
        val nowShowing = runCatching { system.activeNotifications.toList() }.getOrDefault(emptyList())
        for (target in notices.map { it.target }.distinct()) {
            val inGroup = nowShowing.filter { it.tag == TAG && it.notification.group == groupOf(target) }
            if (inGroup.size < 2) continue
            val titles = inGroup.mapNotNull { it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE) }
            val heading = if (target == HubTarget.Shiori) "${inGroup.size} series have new chapters"
            else "${inGroup.size} anime have new episodes"
            val summary = NotificationCompat.Builder(context, channelOf(target))
                .setSmallIcon(R.drawable.ic_stat_hub)
                .setContentTitle(heading)
                .setContentText(titles.joinToString(", "))
                .setStyle(NotificationCompat.InboxStyle().also { s -> titles.take(6).forEach { s.addLine(it) } })
                .setGroup(groupOf(target))
                .setGroupSummary(true)
                .setAutoCancel(true)
            notify(manager, context, TAG_SUMMARY, target.ordinal, summary)
        }
    }

    private fun notify(manager: NotificationManagerCompat, context: Context, tag: String, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        try {
            manager.notify(tag, id, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the post.
        }
    }

    private fun idOf(target: HubTarget, id: String) = "${target.name}:$id".hashCode()

    private fun groupOf(target: HubTarget) = "releases.${target.name}"

    private fun channelOf(target: HubTarget) =
        if (target == HubTarget.Shiori) CHANNEL_CHAPTERS else CHANNEL_EPISODES

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_CHAPTERS, "New chapters", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A series in your Shiori library has a new chapter."
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_EPISODES, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "An anime in your Renzo library has a new episode."
            },
        )
    }

    private fun openIntent(context: Context, notice: ReleaseNotice, requestCode: Int): PendingIntent {
        val intent = Intent(context, HubActivity::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_TARGET, notice.target.name)
            .putExtra(EXTRA_ID, notice.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The series cover, through the Hub's own loader (it signs for the user's servers). Best effort. */
    private suspend fun cover(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withTimeoutOrNull(8_000) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            }.getOrNull()
        }
    }
}
