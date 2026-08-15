package top.levitatemedia.renzo.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.levitatemedia.renzo.tv.ui.screens.ExternalPlayerScreen
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

@Composable
actual fun RenzoBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back on the desktop; screens keep their explicit affordances.
}

actual fun renzoToast(message: String) {
    System.err.println("[toast] $message")
}

@Composable
actual fun rememberRenzoAvatarPicker(
    onPicked: (base64Jpeg: String, contentType: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit = remember {
    {
        val dialog = FileDialog(null as Frame?, "Choose an avatar image", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val n = name.lowercase()
            n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
                n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp")
        }
        dialog.isVisible = true
        val file = dialog.file?.let { File(dialog.directory, it) }
        if (file != null) {
            runCatching {
                val src = ImageIO.read(file) ?: error("Couldn't read that image")
                // Centre-crop to a square, scale to 128 — the web editor's
                // transform, in AWT.
                val side = minOf(src.width, src.height)
                val cropped = src.getSubimage((src.width - side) / 2, (src.height - side) / 2, side, side)
                val scaled = BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB)
                scaled.createGraphics().apply {
                    setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    drawImage(cropped, 0, 0, 128, 128, null)
                    dispose()
                }
                val out = ByteArrayOutputStream()
                ImageIO.write(scaled, "jpg", out)
                onPicked(java.util.Base64.getEncoder().encodeToString(out.toByteArray()), "image/jpeg")
            }.onFailure { onError(it.message ?: "Couldn't read that image") }
        }
    }
}

@Composable
actual fun RenzoPlayerRoute(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onSwitchTitle: (Int, Int, String) -> Unit,
) {
    ExternalPlayerScreen(
        app = app,
        titleId = titleId,
        ep = ep,
        titleName = titleName,
        onExit = onExit,
        onSwitchEpisode = onSwitchEpisode,
    )
}
