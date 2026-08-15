package top.levitatemedia.renzo.tv

import android.widget.Toast
import androidx.compose.runtime.Composable
import top.levitatemedia.renzo.hub.core.HubContextHolder
import top.levitatemedia.renzo.tv.ui.screens.PlayerScreen

@Composable
actual fun RenzoBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual fun renzoToast(message: String) {
    runCatching {
        Toast.makeText(HubContextHolder.context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
actual fun rememberRenzoAvatarPicker(
    onPicked: (base64Jpeg: String, contentType: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val src = context.contentResolver.openInputStream(uri)
                ?.use { android.graphics.BitmapFactory.decodeStream(it) }
                ?: throw Exception("Couldn't read that image")
            // Centre-crop to a square, scale to 128 — same as the web editor.
            val side = minOf(src.width, src.height)
            val cropped = android.graphics.Bitmap.createBitmap(
                src, (src.width - side) / 2, (src.height - side) / 2, side, side,
            )
            val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, 128, 128, true)
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
            onPicked(java.util.Base64.getEncoder().encodeToString(out.toByteArray()), "image/jpeg")
        } catch (e: Exception) {
            onError(e.message ?: "Couldn't read that image")
        }
    }
    return { launcher.launch("image/*") }
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
    PlayerScreen(
        app = app,
        titleId = titleId,
        ep = ep,
        titleName = titleName,
        onExit = onExit,
        onSwitchEpisode = onSwitchEpisode,
        onSwitchTitle = onSwitchTitle,
    )
}
