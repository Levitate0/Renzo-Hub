package app.renzoshiori.client.ui.util

import androidx.compose.runtime.Composable
import app.renzoshiori.client.ShioriRuntime
import top.levitatemedia.renzo.hub.core.offline.DesktopOfflineFiles
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun HubBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back on the desktop; screens keep their explicit affordances.
}

actual fun hubToast(message: String) {
    System.err.println("[toast] $message")
}

private fun pickFile(title: String, filter: ((String) -> Boolean)? = null): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    if (filter != null) dialog.setFilenameFilter { _, name -> filter(name) }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file)
}

@Composable
actual fun rememberImagePicker(onPicked: (bytes: ByteArray?, mimeType: String?) -> Unit): () -> Unit = {
    val f = pickFile("Choose an image") { name ->
        name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp")
    }
    if (f == null) {
        onPicked(null, null)
    } else {
        val mime = when (f.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> null
        }
        onPicked(runCatching { f.readBytes() }.getOrNull(), mime)
    }
}

@Composable
actual fun rememberFileOpenPicker(onPicked: (bytes: ByteArray?, name: String?) -> Unit): () -> Unit = {
    val f = pickFile("Choose a file")
    if (f == null) onPicked(null, null)
    else onPicked(runCatching { f.readBytes() }.getOrNull(), f.name)
}

@Composable
actual fun rememberOfflineFolderPicker(onPicked: (label: String?) -> Unit): () -> Unit = {
    val chooser = JFileChooser().apply {
        dialogTitle = "Choose a download folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val dir = chooser.selectedFile
        (ShioriRuntime.app.offlineStore as? DesktopOfflineFiles)?.setFolder(dir.absolutePath)
        onPicked(dir.absolutePath)
    } else {
        onPicked(null)
    }
}
