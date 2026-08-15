package app.renzoshiori.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.renzoshiori.client.ui.theme.RenzoColors

/**
 * A full-window dialog behind the web's dark overlay (bg-black/80): Compose's
 * platform scrim is much lighter, so a [RenzoColors.DialogScrim] layer fills
 * the window UNDER the content. The scrim layer is a sibling below the
 * content, so clicks on the card never reach the scrim's dismiss — no
 * click-swallowing needed on the content side.
 */
@Composable
fun ScrimDialog(
    onDismiss: () -> Unit,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(RenzoColors.DialogScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Box(modifier = Modifier.align(contentAlignment), content = content)
        }
    }
}
