package app.renzoshiori.client.ui.util

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import app.renzoshiori.client.ShioriRuntime
import top.levitatemedia.renzo.hub.core.HubContextHolder
import top.levitatemedia.renzo.hub.core.HubForeground
import top.levitatemedia.renzo.hub.core.offline.OfflineStore

@Composable
actual fun HubBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual fun hubToast(message: String) {
    runCatching { Toast.makeText(HubContextHolder.context, message, Toast.LENGTH_SHORT).show() }
}

@Composable
actual fun rememberImagePicker(onPicked: (bytes: ByteArray?, mimeType: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onPicked(null, null)
        } else {
            val type = context.contentResolver.getType(uri)
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            onPicked(bytes, type)
        }
    }
    return {
        // Another app's picker takes the foreground; without this the Hub
        // treats the return as a cold open and re-shows the app picker.
        HubForeground.leavingApp()
        launcher.launch(arrayOf("image/*"))
    }
}

@Composable
actual fun rememberFileOpenPicker(onPicked: (bytes: ByteArray?, name: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            onPicked(null, null)
        } else {
            val name = DocumentFile.fromSingleUri(context, uri)?.name
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            onPicked(bytes, name)
        }
    }
    return {
        HubForeground.leavingApp()
        launcher.launch("*/*")
    }
}

@Composable
actual fun rememberOfflineFolderPicker(onPicked: (label: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            onPicked(null)
        } else {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val store = ShioriRuntime.app.offlineStore as? OfflineStore
            store?.setFolder(uri)
            onPicked(store?.folderLabel())
        }
    }
    return {
        HubForeground.leavingApp()
        launcher.launch(null)
    }
}
