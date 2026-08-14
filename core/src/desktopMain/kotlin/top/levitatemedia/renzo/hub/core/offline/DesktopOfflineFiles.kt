package top.levitatemedia.renzo.hub.core.offline

import kotlinx.serialization.json.Json
import top.levitatemedia.renzo.hub.core.hubConfigDir
import top.levitatemedia.renzo.hub.core.keyValuePrefs
import java.io.File
import java.io.OutputStream

/**
 * The desktop's [HubOfflineFiles]: a plain directory tree — the same relative
 * paths the Android SAF store writes — and the same manifest JSON, kept in the
 * same-named settings store under the same key so the format stays one format.
 *
 * The root defaults to `<config dir>/offline` and can be re-pointed from the
 * settings UI (a real directory path, not a SAF tree — HANDOFF §5).
 */
class DesktopOfflineFiles : HubOfflineFiles {
    private val prefs = keyValuePrefs("renzo_offline")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun root(): File {
        val chosen = prefs.getString(KEY_FOLDER, null)?.let(::File)
        val dir = chosen ?: File(hubConfigDir(), "offline")
        dir.mkdirs()
        return dir
    }

    /** Point downloads at a directory of the user's choosing. Null = default. */
    fun setFolder(path: String?) {
        prefs.putString(KEY_FOLDER, path)
    }

    private fun fileFor(relPath: String): File = File(root(), relPath.trimStart('/'))

    override fun readFile(relPath: String): ByteArray? =
        runCatching { fileFor(relPath).takeIf { it.isFile }?.readBytes() }.getOrNull()

    override fun exists(relPath: String): Boolean = fileFor(relPath).exists()

    override fun sizeOf(relPath: String): Long =
        runCatching { fileFor(relPath).takeIf { it.isFile }?.length() ?: 0L }.getOrDefault(0L)

    override fun writeFile(relPath: String, bytes: ByteArray) {
        val f = fileFor(relPath)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    override fun openOutput(relPath: String, append: Boolean): OutputStream? = runCatching {
        val f = fileFor(relPath)
        f.parentFile?.mkdirs()
        java.io.FileOutputStream(f, append)
    }.getOrNull()

    override fun deletePath(relPath: String) {
        runCatching { fileFor(relPath).deleteRecursively() }
    }

    override fun folderLabel(): String? = prefs.getString(KEY_FOLDER, null)

    override fun usableSpaceBytes(): Long = runCatching { root().usableSpace }.getOrDefault(0L)

    override fun kvGet(key: String): String? = prefs.getString("kv_$key", null)
    override fun kvSet(key: String, value: String) = prefs.putString("kv_$key", value)

    @Synchronized
    override fun getManifest(): DownloadManifest =
        prefs.getString(KEY_MANIFEST_V3, null)
            ?.let { runCatching { json.decodeFromString<DownloadManifest>(it) }.getOrNull() }
            ?: DownloadManifest()

    @Synchronized
    override fun setManifest(m: DownloadManifest) {
        prefs.putString(KEY_MANIFEST_V3, json.encodeToString(DownloadManifest.serializer(), m))
    }

    @Synchronized
    override fun updateManifest(block: (DownloadManifest) -> DownloadManifest) {
        setManifest(block(getManifest()))
    }

    private companion object {
        const val KEY_FOLDER = "downloadFolder"
        /** Same key the Android store uses (with its kv_ prefix baked in). */
        const val KEY_MANIFEST_V3 = "kv_renzo.hub.manifest.v3"
    }
}
