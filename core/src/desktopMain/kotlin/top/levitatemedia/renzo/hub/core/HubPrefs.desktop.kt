package top.levitatemedia.renzo.hub.core

import java.io.File
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
private val stores = ConcurrentHashMap<String, KeyValuePrefs>()

actual fun keyValuePrefs(name: String): KeyValuePrefs =
    stores.getOrPut(name) { PropertiesKeyValue(name) }

/** One .properties file per named store, written through on every change. */
private class PropertiesKeyValue(name: String) : KeyValuePrefs {
    private val file = File(hubConfigDir(), "$name.properties")
    private val props = Properties().apply {
        if (file.exists()) runCatching { file.inputStream().use { load(it) } }
    }

    /**
     * Write to a sibling temp file and rename over the target, so the store is
     * never observed half-written. Opening the real file truncates it up
     * front: a crash or a pulled plug mid-write used to leave an empty
     * .properties behind, which reads back as a first run with no server and
     * no session.
     */
    @Synchronized
    private fun save() {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { props.store(it, null) }
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
    }

    @Synchronized
    override fun getString(key: String, def: String?): String? = props.getProperty(key) ?: def

    @Synchronized
    override fun putString(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        save()
    }

    @Synchronized
    override fun getBoolean(key: String, def: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: def

    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    @Synchronized
    override fun getInt(key: String, def: Int): Int = props.getProperty(key)?.toIntOrNull() ?: def
    override fun putInt(key: String, value: Int) = putString(key, value.toString())

    @Synchronized
    override fun getLong(key: String, def: Long): Long = props.getProperty(key)?.toLongOrNull() ?: def
    override fun putLong(key: String, value: Long) = putString(key, value.toString())

    @Synchronized
    override fun getFloat(key: String, def: Float): Float = props.getProperty(key)?.toFloatOrNull() ?: def
    override fun putFloat(key: String, value: Float) = putString(key, value.toString())

    @Synchronized
    override fun remove(key: String) {
        props.remove(key)
        save()
    }

    @Synchronized
    override fun keys(): Set<String> = props.stringPropertyNames()
}
