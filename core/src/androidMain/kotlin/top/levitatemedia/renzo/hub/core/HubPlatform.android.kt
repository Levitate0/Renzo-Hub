package top.levitatemedia.renzo.hub.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The application context for [HubPlatform]'s Android actuals. Set once in
 * Application.onCreate (RenzoApp / HubApplication) before any UI composes.
 */
object HubContextHolder {
    lateinit var context: Context
}

actual object HubPlatform {
    actual fun hasInternet(): Boolean = hasInternet(HubContextHolder.context)

    actual val isTv: Boolean get() = isTvDevice(HubContextHolder.context)

    actual val isDesktop: Boolean = false

    actual val deviceName: String
        get() = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android device"

    actual fun openExternal(url: String) {
        // ACTION_VIEW would otherwise open any scheme (intent:, content:, file:).
        if (!isWebUrl(url)) return
        runCatching {
            HubForeground.leavingApp()
            HubContextHolder.context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    actual fun copySensitive(text: String) {
        runCatching {
            val cm = HubContextHolder.context.getSystemService(android.content.ClipboardManager::class.java)
            val clip = android.content.ClipData.newPlainText("Renzo Hub", text)
            clip.description.extras = android.os.PersistableBundle().apply {
                // The constant arrived in API 33; the same key is honoured by
                // Android 12's clipboard UI, so set it by name below that.
                val key = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    android.content.ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    "android.content.extra.IS_SENSITIVE"
                }
                putBoolean(key, true)
            }
            cm?.setPrimaryClip(clip)
        }
    }
}
