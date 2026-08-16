package top.levitatemedia.renzo.hub.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.renzoshiori.client.ShioriDesktop
import app.renzoshiori.client.ShioriRoot
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import top.levitatemedia.renzo.hub.core.HubSearchFocus
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.tv.RenzoDesktop
import top.levitatemedia.renzo.tv.RenzoDesktopRoot

/**
 * Renzo Hub for desktop — the APK's HubActivity, as a window. Both halves are
 * the SAME composables the Android app ships; this module holds only the
 * window, the picker and the wiring (docs/HANDOFF_renzo-hub_desktop-exe.md §2:
 * do not fork screens).
 */
fun main() {
    // Before any window exists: bind the process to the app's Windows
    // taskbar identity so pinning works (see WindowsIntegration).
    WindowsIntegration.installAppUserModelId()
    // Both halves' signers and download sources exist before any UI, so a
    // restart with a pending download queue fetches authenticated — the
    // mirror of RenzoApp.onCreate + HubApplication on Android.
    ShioriDesktop.install(debugHttp = System.getenv("RENZO_HTTP_DEBUG") == "1")
    RenzoDesktop.install()
    application {
        // Without an explicit icon Windows shows javaw's default coffee cup on
        // the taskbar — the process is javaw.exe, so the window must brand itself.
        val appIcon = remember {
            runCatching { BitmapPainter(useResource("renzohub-icon.png", ::loadImageBitmap)) }.getOrNull()
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Renzo Hub",
            icon = appIcon,
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
            // ⌘K / Ctrl-K focuses the active half's topbar search (web parity;
            // each half registers its field in HubSearchFocus).
            onPreviewKeyEvent = { e ->
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    e.key == androidx.compose.ui.input.key.Key.K &&
                    (e.isCtrlPressed || e.isMetaPressed)
                ) {
                    runCatching { HubSearchFocus.requester?.requestFocus() }
                    true
                } else {
                    false
                }
            },
        ) {
            // Windows-style middle-click autoscroll over the whole window
            // (see AutoScroll.kt) — an AWT-level compat layer, so every
            // scrollable in the app gets it without wiring.
            val autoScroll = remember { MiddleClickAutoScroll(window) }
            DisposableEffect(Unit) {
                autoScroll.install()
                onDispose { autoScroll.uninstall() }
            }

            // HubActivity's picker state, verbatim: null = picker, and the
            // active target drives which credentials sign shared fetches
            // (images, downloads). Not persisted — same as the APK.
            var target by remember { mutableStateOf<HubTarget?>(null) }
            LaunchedEffect(target) { target?.let { HubSession.setActive(it) } }

            Box(Modifier.fillMaxSize()) {
                when (target) {
                    null -> DesktopPicker(onPick = { target = it })
                    HubTarget.Renzo -> RenzoDesktopRoot(
                        onSwitchApp = { target = HubTarget.Shiori },
                        onBackToPicker = { target = null },
                    )
                    HubTarget.Shiori -> ShioriRoot(
                        onSwitchApp = { target = HubTarget.Renzo },
                        onBackToPicker = { target = null },
                    )
                }
                autoScroll.anchor.value?.let { AutoScrollAnchorBadge(it) }
            }
        }
    }
}
