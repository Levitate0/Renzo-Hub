package top.levitatemedia.renzo.hub.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * The Hub's config directory: %APPDATA%\RenzoHub on Windows,
 * ~/Library/Application Support/RenzoHub on macOS, XDG config on Linux.
 */
fun hubConfigDir(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base = when {
        os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
        os.contains("mac") -> File(home, "Library/Application Support")
        else -> System.getenv("XDG_CONFIG_HOME")?.let(::File) ?: File(home, ".config")
    }
    return File(base, "RenzoHub").apply { mkdirs() }
}

/**
 * ONE store per name, process-wide.
 *
 * This cache is not an optimisation — it is what makes the store correct.
 * Each PropertiesKeyValue holds its own in-memory snapshot and writes the
 * WHOLE map on every change, so two instances over the same file are a
 * read-modify-write race: whichever writes last silently reverts the other's
 * keys to whatever they were when it loaded.
 *
 * That is not hypothetical here. Two Prefs objects exist over "renzo_tv" —
 * AppServices.kt:24 (the UI) and RenzoOffline.kt:131 (the downloader wiring,
 * built at startup before anyone has logged in). The downloader's copy is
 * handed to an ApiClient that WRITES: `prefs.sessionCookie = v` when the
 * server rotates the cookie, and `prefs.clearSession()` on a 401. Each of
 * those flushed a startup-era snapshot over the live file and erased the
 * serverUrl and session the login screen had just saved — which is why the
 * desktop app "sometimes" forgot the server address and credentials while
 * Android, whose SharedPreferences are already shared per name, never did.
 */
/** A temp file older than this was orphaned by a crash, not a live write. */
private const val STALE_TEMP_MS = 60_000L

private val stores = ConcurrentHashMap<String, KeyValuePrefs>()

actual fun keyValuePrefs(name: String): KeyValuePrefs =
    stores.getOrPut(name) { PropertiesKeyValue(name) }

/**
 * One .properties file per named store.
 *
 * Every mutation is a RE-READ, apply, write — under an exclusive file lock —
 * rather than a flush of an in-memory snapshot. That matters because the whole
 * map is written at once, so a writer holding a stale snapshot silently reverts
 * every key it has not seen.
 *
 * The process-wide cache above fixes that only WITHIN a process. Two processes
 * are just as real here and the cache cannot help:
 *
 *  * the Windows installer force-kills a running RenzoHub during an upgrade, so
 *    the old instance can be writing right up to the moment it dies;
 *  * nothing stops a second window being launched.
 *
 * and the renzo half writes on a ticker (`resume:<key>` during playback), so a
 * second instance is not idle — it is the most active writer in the app. A
 * snapshot taken before login, flushed by one of those ticks, is exactly how
 * the server URL kept disappearing and taking the session with it.
 *
 * Reads reload when the file has changed underneath, so a value another process
 * wrote is visible rather than shadowed by our copy.
 */
private class PropertiesKeyValue(name: String) : KeyValuePrefs {
    private val file = File(hubConfigDir(), "$name.properties")

    /**
     * A sidecar lock file, never the data file: the write below REPLACES the
     * data file by rename, and a lock on a path that gets replaced protects
     * nothing.
     */
    private val lockFile = File(hubConfigDir(), "$name.properties.lock")

    private var props = Properties()
    private var loadedStamp = -1L

    init {
        reloadIfChanged()
    }

    /** Re-read when the file has changed since we last looked. */
    @Synchronized
    private fun reloadIfChanged() {
        val stamp = if (file.exists()) file.lastModified() else 0L
        if (stamp == loadedStamp) return
        val fresh = Properties()
        if (file.exists()) runCatching { file.inputStream().use { fresh.load(it) } }
        props = fresh
        loadedStamp = stamp
    }

    private fun <T> underLock(block: () -> T): T =
        runCatching {
            RandomAccessFile(lockFile, "rw").use { raf ->
                raf.channel.use { channel -> channel.lock().use { block() } }
            }
        }.getOrElse {
            // A filesystem that cannot lock (some network shares) must not make
            // the app unusable — fall back to unlocked, which is still no worse
            // than the previous behaviour.
            block()
        }

    /**
     * Read the file, apply one change, write it back atomically, all while
     * holding the lock. Writing to a sibling temp file and renaming keeps a
     * crash or a pulled plug from leaving a half-written store, which used to
     * read back as a first run with no server and no session.
     */
    @Synchronized
    private fun mutate(apply: (Properties) -> Unit) {
        underLock {
            val disk = Properties()
            if (file.exists()) runCatching { file.inputStream().use { disk.load(it) } }
            apply(disk)
            runCatching {
                // A UNIQUE temp name, not a shared one. Two processes writing
                // through the same "<name>.properties.tmp" is not merely a lost
                // update — one process's store() creates the temp and the
                // other's move() takes it, so the write throws
                // NoSuchFileException and the value never lands at all.
                // Measured: with a shared name, a login racing a playback
                // ticker lost the server URL in 5 runs out of 5. The lock below
                // already serialises this, so the unique name is insurance for
                // the fallback path where locking is unavailable.
                val tmp = File(file.parentFile, "${file.name}.${ProcessHandle.current().pid()}.${System.nanoTime()}.tmp")
                tmp.outputStream().use { disk.store(it, null) }
                runCatching {
                    Files.move(
                        tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                    )
                }.recoverCatching {
                    // Some filesystems (and Windows, when the target is open in
                    // another handle) refuse ATOMIC_MOVE. A plain replace still
                    // beats writing into the live file.
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }.getOrThrow()
            }
            props = disk
            loadedStamp = if (file.exists()) file.lastModified() else 0L
            // A temp left behind by a crash would otherwise accumulate forever.
            runCatching {
                file.parentFile?.listFiles { f: File ->
                    f.name.startsWith("${file.name}.") && f.name.endsWith(".tmp") &&
                        System.currentTimeMillis() - f.lastModified() > STALE_TEMP_MS
                }?.forEach { it.delete() }
            }
        }
    }

    @Synchronized
    override fun getString(key: String, def: String?): String? {
        reloadIfChanged()
        return props.getProperty(key) ?: def
    }

    override fun putString(key: String, value: String?) =
        mutate { if (value == null) it.remove(key) else it.setProperty(key, value) }

    @Synchronized
    override fun getBoolean(key: String, def: Boolean): Boolean {
        reloadIfChanged()
        return props.getProperty(key)?.toBooleanStrictOrNull() ?: def
    }

    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    @Synchronized
    override fun getInt(key: String, def: Int): Int {
        reloadIfChanged()
        return props.getProperty(key)?.toIntOrNull() ?: def
    }

    override fun putInt(key: String, value: Int) = putString(key, value.toString())

    @Synchronized
    override fun getLong(key: String, def: Long): Long {
        reloadIfChanged()
        return props.getProperty(key)?.toLongOrNull() ?: def
    }

    override fun putLong(key: String, value: Long) = putString(key, value.toString())

    @Synchronized
    override fun getFloat(key: String, def: Float): Float {
        reloadIfChanged()
        return props.getProperty(key)?.toFloatOrNull() ?: def
    }

    override fun putFloat(key: String, value: Float) = putString(key, value.toString())

    override fun remove(key: String) = mutate { it.remove(key) }

    @Synchronized
    override fun keys(): Set<String> {
        reloadIfChanged()
        return props.stringPropertyNames()
    }
}
