package top.levitatemedia.renzo.hub.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.ConcurrentHashMap

private val secretStores = ConcurrentHashMap<String, SecretPrefs>()

/**
 * EncryptedSharedPreferences in the file "<name>.xml", which backup_rules.xml
 * and data_extraction_rules.xml must exclude: Keystore keys never leave the
 * device, so a restored copy could only ever be undecryptable.
 */
actual fun secretPrefs(name: String): SecretPrefs = secretStores.getOrPut(name) { EncryptedSecretPrefs(name) }

/**
 * Opened lazily and retried on every access until it opens, like Shiori's
 * AndroidTokenStore: before the first unlock after a reboot the Keystore is
 * unavailable, and pinning that answer for the life of the process is how
 * sign-ins used to vanish. A store that is unreadable for good (its key was
 * lost) is wiped instead, which costs one sign-in.
 */
private class EncryptedSecretPrefs(private val name: String) : SecretPrefs {
    @Volatile private var opened: SharedPreferences? = null

    /** Its own Keystore key: a reset of Shiori's store must not take this one with it. */
    private val keyAlias = "${name}_master_key"

    private fun store(): SharedPreferences? {
        opened?.let { return it }
        synchronized(this) {
            opened?.let { return it }
            val context = HubContextHolder.context.applicationContext
            val prefs = try {
                open(context)
            } catch (first: Throwable) {
                if (!userUnlocked(context)) {
                    Log.w("HubSecrets", "Keystore locked (pre-unlock boot); $name will open once unlocked")
                    return null
                }
                runCatching { open(context) }.getOrNull() ?: run {
                    Log.w("HubSecrets", "$name unreadable; resetting it, sign-in required", first)
                    runCatching { context.deleteSharedPreferences(name) }
                    runCatching {
                        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
                    }
                    runCatching { open(context) }.onFailure {
                        Log.e("HubSecrets", "$name unavailable; secrets stay in plain settings", it)
                    }.getOrNull() ?: return null
                }
            }
            opened = prefs
            return prefs
        }
    }

    private fun open(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, keyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun userUnlocked(context: Context): Boolean =
        runCatching { context.getSystemService(android.os.UserManager::class.java)?.isUserUnlocked }
            .getOrNull() ?: true

    override fun isAvailable(): Boolean = store() != null

    /**
     * Decrypted values, by key. The session is read for every request,
     * images included, and each EncryptedSharedPreferences read decrypts.
     * One instance per name and every write goes through [putString], so the
     * cache cannot go stale within the process.
     */
    private val cache = HashMap<String, String?>()

    override fun getString(key: String): String? {
        val prefs = store() ?: return null
        synchronized(cache) {
            if (key in cache) return cache[key]
            return prefs.getString(key, null).also { cache[key] = it }
        }
    }

    // commit(): a session is written once per sign-in or rotation, and an
    // apply() lost to a process kill is a sign-in the user has to repeat.
    // Its result is the answer: SecretValue deletes the plain copy only on
    // true, so reporting a failed write as success would sign the user out.
    @Suppress("ApplySharedPref")
    override fun putString(key: String, value: String?): Boolean {
        val prefs = store() ?: return false
        synchronized(cache) {
            val ok = runCatching {
                prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
            }.getOrDefault(false)
            // A failed commit may still have changed the in-memory map; re-read
            // it rather than trust either answer.
            if (ok) cache[key] = value else cache.remove(key)
            return ok
        }
    }
}
