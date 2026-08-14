package top.levitatemedia.renzo.hub.core

import java.net.NetworkInterface

actual object HubPlatform {
    actual fun hasInternet(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .any { it.isUp && !it.isLoopback }
    }.getOrDefault(true) // when in doubt, let the request itself decide

    actual val isTv: Boolean = false

    actual val deviceName: String =
        runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "Desktop"

    actual fun openExternal(url: String) {
        runCatching {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            }
        }
    }
}
