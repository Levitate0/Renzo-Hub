package top.levitatemedia.renzo.hub.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import top.levitatemedia.renzo.hub.core.GeistFamily

/**
 * Step 1 of the desktop sequencing (handoff §8): a Compose window running
 * against the Hub's :core — proving the KMP toolchain, the shared source
 * sets and the platform font seam before any screen moves. The real screens
 * arrive by moving feature-module code to commonMain, never by rebuilding
 * them here.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Renzo Hub",
        state = rememberWindowState(width = 1280.dp, height = 820.dp),
    ) {
        HubDesktopRoot()
    }
}

// Web tokens (globals.css :root) — the same values RenzoColors carries on
// Android; lifted inline until the theme objects move to commonMain.
private val Background = Color(0xFF0C0A09)
private val Foreground = Color(0xFFF2F2F2)
private val MutedForeground = Color(0xFFA1A1AA)

@Composable
private fun HubDesktopRoot() {
    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Renzo Hub",
                color = Foreground,
                fontFamily = GeistFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
            )
            Text(
                "Desktop toolchain online — :core (KMP), Geist, Compose Multiplatform",
                color = MutedForeground,
                fontFamily = GeistFamily,
                fontSize = 13.sp,
            )
        }
    }
}
