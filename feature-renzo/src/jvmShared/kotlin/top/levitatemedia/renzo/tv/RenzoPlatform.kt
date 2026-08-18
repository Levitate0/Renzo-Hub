package top.levitatemedia.renzo.tv

import androidx.compose.runtime.Composable

/**
 * The Renzo half's platform seams — the same shapes :feature-shiori uses.
 * Android actuals delegate to the system (BackHandler, Toast); the desktop
 * has no system back and logs toasts.
 */

/** System back interception; no-op on desktop. */
@Composable
expect fun RenzoBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Viewport width in dp — the LocalConfiguration.screenWidthDp these screens
 * were written against, from window metrics so it tracks desktop resizes.
 */
@Composable
fun renzoScreenWidthDp(): Int =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp().value.toInt()
    }

/** Viewport height in dp — a 1080p television is only ~540dp tall. */
@Composable
fun renzoScreenHeightDp(): Int =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp().value.toInt()
    }

/**
 * Avatar picker: opens the platform image chooser, centre-crops to a square,
 * scales to 128px and hands back a base64 JPEG + content type — the web
 * editor's exact transform, done per-platform (Android Bitmap ⇄ AWT ImageIO).
 */
@Composable
expect fun rememberRenzoAvatarPicker(
    onPicked: (base64Jpeg: String, contentType: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit

/** Fire-and-forget notice. Android: Toast; desktop: stderr. */
expect fun renzoToast(message: String)

/**
 * The player route (RENZO-DESKTOP-NATIVE-HANDOFF.md §6.2): Android renders
 * the Media3 PlayerScreen; the desktop renders the external-player hand-off
 * screen (resolve → launch mpv/VLC → mark watched), with embedded VLCJ to
 * follow behind the same seam.
 */
@Composable
expect fun RenzoPlayerRoute(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onSwitchTitle: (Int, Int, String) -> Unit,
)
