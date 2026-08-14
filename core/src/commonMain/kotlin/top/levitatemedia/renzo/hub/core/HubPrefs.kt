package top.levitatemedia.renzo.hub.core

/**
 * Plain key/value settings — the client's localStorage. Android backs each
 * named store with the SharedPreferences file OF THE SAME NAME (so existing
 * installs keep every setting); the desktop backs it with a .properties file
 * in the OS config dir.
 *
 * Not for secrets — tokens live in the platform TokenStore implementations.
 */
interface KeyValuePrefs {
    fun getString(key: String, def: String? = null): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, def: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, def: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, def: Long): Long
    fun putLong(key: String, value: Long)
    fun getFloat(key: String, def: Float): Float
    fun putFloat(key: String, value: Float)
    fun remove(key: String)
    fun keys(): Set<String>
}

/** Open (or create) the named settings store. */
expect fun keyValuePrefs(name: String): KeyValuePrefs
