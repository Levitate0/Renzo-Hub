package top.levitatemedia.renzo.hub.core

import java.io.File
import java.util.Properties

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

actual fun keyValuePrefs(name: String): KeyValuePrefs = PropertiesKeyValue(name)

/** One .properties file per named store, written through on every change. */
private class PropertiesKeyValue(name: String) : KeyValuePrefs {
    private val file = File(hubConfigDir(), "$name.properties")
    private val props = Properties().apply {
        if (file.exists()) runCatching { file.inputStream().use { load(it) } }
    }

    @Synchronized
    private fun save() {
        runCatching { file.outputStream().use { props.store(it, null) } }
    }

    override fun getString(key: String, def: String?): String? = props.getProperty(key) ?: def
    override fun putString(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        save()
    }

    override fun getBoolean(key: String, def: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: def

    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getInt(key: String, def: Int): Int = props.getProperty(key)?.toIntOrNull() ?: def
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getLong(key: String, def: Long): Long = props.getProperty(key)?.toLongOrNull() ?: def
    override fun putLong(key: String, value: Long) = putString(key, value.toString())
    override fun getFloat(key: String, def: Float): Float = props.getProperty(key)?.toFloatOrNull() ?: def
    override fun putFloat(key: String, value: Float) = putString(key, value.toString())

    override fun remove(key: String) {
        props.remove(key)
        save()
    }

    override fun keys(): Set<String> = props.stringPropertyNames()
}
