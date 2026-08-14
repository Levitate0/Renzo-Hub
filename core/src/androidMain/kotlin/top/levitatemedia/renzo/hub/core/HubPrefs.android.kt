package top.levitatemedia.renzo.hub.core

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences under the same file name — existing installs keep everything. */
actual fun keyValuePrefs(name: String): KeyValuePrefs =
    SharedPrefsKeyValue(HubContextHolder.context.getSharedPreferences(name, Context.MODE_PRIVATE))

private class SharedPrefsKeyValue(private val p: SharedPreferences) : KeyValuePrefs {
    override fun getString(key: String, def: String?): String? = p.getString(key, def)
    override fun putString(key: String, value: String?) {
        p.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    override fun getBoolean(key: String, def: Boolean): Boolean = p.getBoolean(key, def)
    override fun putBoolean(key: String, value: Boolean) = p.edit().putBoolean(key, value).apply()
    override fun getInt(key: String, def: Int): Int = p.getInt(key, def)
    override fun putInt(key: String, value: Int) = p.edit().putInt(key, value).apply()
    override fun getLong(key: String, def: Long): Long = p.getLong(key, def)
    override fun putLong(key: String, value: Long) = p.edit().putLong(key, value).apply()
    override fun getFloat(key: String, def: Float): Float = p.getFloat(key, def)
    override fun putFloat(key: String, value: Float) = p.edit().putFloat(key, value).apply()
    override fun remove(key: String) = p.edit().remove(key).apply()
    override fun keys(): Set<String> = p.all.keys
}
