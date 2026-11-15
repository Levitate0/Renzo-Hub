package top.levitatemedia.renzo.hub.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins where a session goes while the encrypted store is locked, and that a sign-out then sticks. */
class SecretValueTest {
    private class MapPrefs : KeyValuePrefs {
        val m = HashMap<String, String>()
        override fun getString(key: String, def: String?) = m[key] ?: def
        override fun putString(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
        override fun getBoolean(key: String, def: Boolean) = def
        override fun putBoolean(key: String, value: Boolean) = Unit
        override fun getInt(key: String, def: Int) = def
        override fun putInt(key: String, value: Int) = Unit
        override fun getLong(key: String, def: Long) = def
        override fun putLong(key: String, value: Long) = Unit
        override fun getFloat(key: String, def: Float) = def
        override fun putFloat(key: String, value: Float) = Unit
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys
    }

    private class FakeSecrets : SecretPrefs {
        var available = true
        var failWrites = false
        val m = HashMap<String, String>()
        override fun isAvailable() = available
        override fun getString(key: String) = if (available) m[key] else null
        override fun putString(key: String, value: String?): Boolean {
            if (!available || failWrites) return false
            if (value == null) m.remove(key) else m[key] = value
            return true
        }
    }

    private val secrets = FakeSecrets()
    private val legacy = MapPrefs()
    private val pending = MapPrefs()
    private val v = SecretValue(secrets, legacy, "session", pending)

    @Test
    fun upgradeLiftsPlainValue() {
        legacy.m["session"] = "old"
        assertEquals("old", v.get())
        assertEquals("old", secrets.m["session"])
        assertNull(legacy.m["session"])
    }

    @Test
    fun lockedWriteStaysOutOfTheBackedUpStore() {
        secrets.available = false
        v.set("new")
        assertNull(legacy.m["session"])
        assertEquals("new", v.get())
        secrets.available = true
        assertEquals("new", v.get())
        assertNull(pending.m["session"])
    }

    @Test
    fun lockedSignOutIsNotUndoneOnUnlock() {
        secrets.m["session"] = "old"
        secrets.available = false
        v.set(null)
        assertNull(v.get())
        secrets.available = true
        assertNull(v.get())
        assertNull(secrets.m["session"])
    }

    @Test
    fun failedLiftKeepsThePlainCopy() {
        legacy.m["session"] = "old"
        secrets.failWrites = true
        v.get()
        assertEquals("old", legacy.m["session"])
    }
}
