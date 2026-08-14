package top.levitatemedia.renzo.hub.core.offline

import java.io.OutputStream

/**
 * The platform seam under the offline system: relative-path file storage plus
 * the persisted download manifest. Android implements it over SAF/app-private
 * storage ([OfflineStore]); the desktop implements it over a plain directory.
 * Everything above this line — [OfflineLibrary], the repositories, every
 * screen — is platform-free.
 */
interface HubOfflineFiles {
    fun readFile(relPath: String): ByteArray?
    fun exists(relPath: String): Boolean
    fun sizeOf(relPath: String): Long
    fun writeFile(relPath: String, bytes: ByteArray)
    fun openOutput(relPath: String, append: Boolean = false): OutputStream?
    fun deletePath(relPath: String)

    /** Human-readable label for the chosen storage folder, null if default. */
    fun folderLabel(): String?
    fun usableSpaceBytes(): Long

    /** Small key/value stash the download system shares with its UI. */
    fun kvGet(key: String): String?
    fun kvSet(key: String, value: String)

    fun getManifest(): DownloadManifest
    fun setManifest(m: DownloadManifest)
    fun updateManifest(block: (DownloadManifest) -> DownloadManifest)
}
