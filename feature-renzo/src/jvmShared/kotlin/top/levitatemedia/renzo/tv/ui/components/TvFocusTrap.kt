package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * On a television, an in-window overlay does NOT contain D-pad focus: the
 * cursor tunnels through the scrim into the (covered) screen behind it and
 * gets lost — the same failure ScrimDialog fixes on the Shiori half. A
 * Dialog is its own window and traps focus natively, so TV pop-outs render
 * inside one; everywhere else the overlay stays in-window, where a mouse or
 * finger can't wander behind it anyway.
 */
@Composable
fun TvFocusTrap(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (top.levitatemedia.renzo.hub.core.HubPlatform.isTv) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            content()
        }
    } else {
        content()
    }
}
