package top.levitatemedia.renzo.hub.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import app.renzoshiori.client.ShioriDesktop
import app.renzoshiori.client.ShioriRoot

/**
 * Renzo Hub for desktop. The Shiori (manga) half ships first
 * (docs/HANDOFF_renzo-hub_desktop-exe.md §6); the Renzo half joins behind the
 * VideoPlayer seam, at which point this grows the same picker the APK has.
 *
 * ShioriRoot is the SAME composable the Android app ships — Connect, Login,
 * shell, library, reader, settings, downloads. Nothing here but the window.
 */
fun main() {
    ShioriDesktop.install(debugHttp = System.getenv("RENZO_HTTP_DEBUG") == "1")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Renzo Hub",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            ShioriRoot(onSwitchApp = null)
        }
    }
}
