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

    actual val deviceName: String
        get() = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android device"

    actual fun openExternal(url: String) {
        runCatching {
            HubForeground.leavingApp()
            HubContextHolder.context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
