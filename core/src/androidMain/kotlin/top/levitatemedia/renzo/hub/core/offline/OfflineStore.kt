package top.levitatemedia.renzo.hub.core.offline

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream

/**
 * Shared offline storage for both halves of the Hub — file/SAF writes, the
 * manifest, and the chosen download folder.
 *
 * Generalised from the Shiori client's RenzoStore, and deliberately keeping two
 * of its details byte-for-byte:
 *
 *  - **The same SharedPreferences file** (`renzo_offline`). SAF reads resolve
 *    through memoised `doc_<relPath>` URIs rather than by walking the tree, so
 *    a different prefs file would orphan every chapter a user has already
 *    downloaded even though the files are still on disk.
 *  - **The same default root** (`getExternalFilesDir("offline")`), so non-SAF
 *    installs resolve their existing paths unchanged.
 *
 * New writes go under [ROOT_DIR]; pre-existing Shiori files live under
 * `RenzoShiori/` and stay readable through their memoised URIs. Nothing is
 * moved — rewriting paths would mean shifting gigabytes for no benefit.
 *
 * Namespacing between the two halves is carried in the relPath itself (callers
 * pass `shiori/…` or `renzo/…`), not in the root, precisely so that the old
 * unprefixed `offline/…` paths keep resolving.
 */
class OfflineStore(private val context: Context) : HubOfflineFiles {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile private var noMediaEnsured = false

    private val defaultRoot: File
        get() = context.getExternalFilesDir("offline") ?: File(context.filesDir, "offline")

    private fun treeUri(): Uri? = prefs.getString(KEY_TREE, null)?.let { Uri.parse(it) }

    private fun segments(relPath: String): List<String> =
        relPath.replace("\\", "/").split("/").filter { it.isNotEmpty() && it != "." && it != ".." }

    private fun safDir(create: Boolean, dirSegs: List<String>): DocumentFile? {
        val tree = treeUri() ?: return null
        var dir = DocumentFile.fromTreeUri(context, tree) ?: return null
        for (name in listOf(ROOT_DIR) + dirSegs) {
            val next = dir.findFile(name)
            dir = when {
                next != null && next.isDirectory -> next
                create -> dir.createDirectory(name) ?: return null
                else -> return null
            }
        }
        return dir
    }

    private fun docKey(relPath: String) = "doc_$relPath"
    private fun rememberDoc(relPath: String, uri: Uri) =
        prefs.edit().putString(docKey(relPath), uri.toString()).apply()
    private fun rememberedDoc(relPath: String): Uri? =
        prefs.getString(docKey(relPath), null)?.let { Uri.parse(it) }

    /**
     * Drop a `.nomedia` marker at the download root so MediaStore skips it.
     * This matters more for the Hub than it did for the reader alone: without
     * it, downloaded episodes turn up in the system gallery and in every
     * file-picker on the device.
     */
    private fun ensureNoMedia() {
        if (noMediaEnsured) return
        try {
            if (treeUri() != null) {
                val dir = safDir(true, emptyList()) ?: return
                if (dir.findFile(NO_MEDIA) == null) dir.createFile("application/octet-stream", NO_MEDIA)
            } else {
                val f = File(defaultRoot, NO_MEDIA)
                if (!f.exists()) { f.parentFile?.mkdirs(); f.createNewFile() }
            }
            noMediaEnsured = true
        } catch (_: Exception) {
            // Best effort: a provider that rejects a dot-file just means the
            // files may be indexed. Never fail a download over it.
        }
    }

    // ── reads ────────────────────────────────────────────────────────────────

    /** Whole-file read. Pages and subtitles only — never call this for video. */
    override fun readFile(relPath: String): ByteArray? =
        if (treeUri() != null) {
            rememberedDoc(relPath)?.let {
                runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull()
            }
        } else {
            File(defaultRoot, segments(relPath).joinToString("/")).takeIf { it.exists() }?.readBytes()
        }

    /**
     * A URI the player can stream from. This is the whole reason video works:
     * a multi-gigabyte episode cannot be read into a ByteArray, and Media3
     * takes a `content://` or `file://` URI directly.
     */
    fun uriFor(relPath: String): Uri? =
        if (treeUri() != null) {
            rememberedDoc(relPath)
        } else {
            File(defaultRoot, segments(relPath).joinToString("/"))
                .takeIf { it.exists() }
                ?.let { Uri.fromFile(it) }
        }

    override fun exists(relPath: String): Boolean =
        if (treeUri() != null) rememberedDoc(relPath) != null
        else File(defaultRoot, segments(relPath).joinToString("/")).exists()

    /** Bytes already on disk, for resuming a partial transfer. */
    override fun sizeOf(relPath: String): Long =
        if (treeUri() != null) {
            rememberedDoc(relPath)
                ?.let { runCatching { DocumentFile.fromSingleUri(context, it)?.length() }.getOrNull() }
                ?: 0L
        } else {
            File(defaultRoot, segments(relPath).joinToString("/")).let { if (it.exists()) it.length() else 0L }
        }

    // ── writes ───────────────────────────────────────────────────────────────

    override fun writeFile(relPath: String, bytes: ByteArray) {
        openOutput(relPath, append = false)?.use { it.write(bytes) }
    }

    /**
     * Streaming write. [append] resumes a partial file rather than truncating,
     * which is what makes a 2 GB episode survive a dropped connection.
     */
    override fun openOutput(relPath: String, append: Boolean): OutputStream? {
        ensureNoMedia()
        return if (treeUri() != null) {
            val segs = segments(relPath)
            val parent = safDir(true, segs.dropLast(1)) ?: return null
            val name = segs.last()
            val doc = parent.findFile(name)
                ?: parent.createFile("application/octet-stream", name)
                ?: return null
            rememberDoc(relPath, doc.uri)
            // "wa" appends, "wt" truncates. Not every provider honours "wa";
            // callers must verify with sizeOf() before trusting a resume.
            runCatching { context.contentResolver.openOutputStream(doc.uri, if (append) "wa" else "wt") }.getOrNull()
        } else {
            val f = File(defaultRoot, segments(relPath).joinToString("/"))
            f.parentFile?.mkdirs()
            runCatching { java.io.FileOutputStream(f, append) }.getOrNull()
        }
    }

    override fun deletePath(relPath: String) {
        if (treeUri() != null) {
            val prefix = docKey(relPath)
            val e = prefs.edit()
            for ((k, v) in prefs.all) {
                if (k == prefix || k.startsWith("$prefix/")) {
                    runCatching { DocumentFile.fromSingleUri(context, Uri.parse(v as String))?.delete() }
                    e.remove(k)
                }
            }
            e.apply()
            runCatching { safDir(false, segments(relPath))?.delete() }
        } else {
            File(defaultRoot, segments(relPath).joinToString("/")).deleteRecursively()
        }
    }

    // ── download folder ──────────────────────────────────────────────────────

    override fun folderLabel(): String? = treeUri()?.let {
        runCatching { DocumentFile.fromTreeUri(context, it)?.name ?: Uri.decode(it.lastPathSegment) }.getOrNull()
    }

    fun setFolder(uri: Uri?) {
        noMediaEnsured = false // a new folder needs its own marker
        prefs.edit().apply {
            if (uri != null) putString(KEY_TREE, uri.toString()) else remove(KEY_TREE)
            apply()
        }
    }

    /** Free space at the download location, for pre-flighting a large item. */
    override fun usableSpaceBytes(): Long = runCatching {
        if (treeUri() != null) {
            context.contentResolver.openFileDescriptor(treeUri()!!, "r")?.use {
                android.os.StatFs(defaultRoot.path).availableBytes
            } ?: android.os.StatFs(defaultRoot.path).availableBytes
        } else {
            defaultRoot.mkdirs()
            android.os.StatFs(defaultRoot.path).availableBytes
        }
    }.getOrDefault(0L)

    // ── small key/value settings ─────────────────────────────────────────────
    // Same `kv_` prefix and same prefs file the standalone client used, so
    // settings like auto-purge survive the upgrade rather than silently resetting.

    override fun kvGet(key: String): String? = prefs.getString("kv_$key", null)

    override fun kvSet(key: String, value: String) = prefs.edit().putString("kv_$key", value).apply()

    // ── manifest ─────────────────────────────────────────────────────────────

    @Synchronized
    override fun getManifest(): DownloadManifest {
        prefs.getString(KEY_MANIFEST_V3, null)?.let { raw ->
            runCatching { json.decodeFromString<DownloadManifest>(raw) }.getOrNull()?.let { return it }
        }
        // First run on the Hub: adopt whatever the standalone Shiori client
        // left behind. The old blob is deliberately NOT deleted — it is the
        // rollback path for one release.
        val migrated = ManifestMigration.fromV2Json(prefs.getString(KEY_MANIFEST_V2, null))
        if (migrated != null) {
            setManifest(migrated)
            return migrated
        }
        return DownloadManifest()
    }

    @Synchronized
    override fun setManifest(m: DownloadManifest) {
        prefs.edit().putString(KEY_MANIFEST_V3, json.encodeToString(m)).apply()
    }

    @Synchronized
    override fun updateManifest(block: (DownloadManifest) -> DownloadManifest) {
        setManifest(block(getManifest()))
    }

    private companion object {
        const val PREFS_NAME = "renzo_offline"
        const val KEY_TREE = "downloadTree"
        const val NO_MEDIA = ".nomedia"
        /** New writes land here; pre-Hub Shiori files remain under RenzoShiori/. */
        const val ROOT_DIR = "RenzoHub"
        const val KEY_MANIFEST_V3 = "kv_renzo.hub.manifest.v3"
        /** The standalone client's key. Its payload is format v2 despite the name. */
        const val KEY_MANIFEST_V2 = "kv_renzo.offline.manifest.v1"
    }
}
