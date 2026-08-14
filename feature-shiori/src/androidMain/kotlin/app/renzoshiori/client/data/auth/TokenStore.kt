package app.renzoshiori.client.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credentials and connection details.
 *
 * Split across TWO stores on purpose:
 *
 *  - **Secrets** (the JWT and the remember-me cookie) live in the encrypted
 *    store, which can become permanently undecryptable and is then wiped.
 *  - **Connection details** (server URL, last username) live in plain
 *    SharedPreferences, because they are not secrets — the URL is typed on the
 *    Connect screen, shown in settings, and printed in letterbox type on the TV
 *    pairing screen.
 *
 * That split is the whole point. Previously everything shared the encrypted
 * file, so a single Keystore failure wiped the server address along with the
 * token: the app came back looking like a fresh install and asked the user to
 * retype a URL that was never lost. Now the worst case costs a sign-in, not the
 * connection.
 */
class TokenStore(context: Context) {
    /** True when the encrypted store had to be thrown away on this launch. */
    var wasReset: Boolean = false
        private set

    private val secrets: SharedPreferences = openOrReset(context)

    /** Not secret, and deliberately outside the store that can be wiped. */
    private val conn: SharedPreferences =
        context.applicationContext.getSharedPreferences(CONN_PREFS, Context.MODE_PRIVATE)

    init {
        // One-time lift of values written before the split, so existing installs
        // keep their server without a re-Connect.
        if (!conn.contains(KEY_SERVER)) {
            secrets.getString(KEY_SERVER, null)?.let { conn.edit().putString(KEY_SERVER, it).apply() }
        }
        if (!conn.contains(KEY_USERNAME)) {
            secrets.getString(KEY_USERNAME, null)?.let { conn.edit().putString(KEY_USERNAME, it).apply() }
        }
    }

    var accessToken: String?
        get() = secrets.getString(KEY_TOKEN, null)
        set(value) = secrets.edit().putString(KEY_TOKEN, value).apply()

    var serverUrl: String?
        get() = conn.getString(KEY_SERVER, null)
        // commit(), not apply(): losing this to a process death is what makes
        // the app look like it forgot the server.
        set(value) { conn.edit().putString(KEY_SERVER, value).commit() }

    var lastUsername: String?
        get() = conn.getString(KEY_USERNAME, null)
        set(value) { conn.edit().putString(KEY_USERNAME, value).commit() }

    /**
     * The server's httpOnly `refresh_token` cookie, kept so "Remember me"
     * survives the app being closed. Holding it only in OkHttp's in-memory
     * cookie jar meant the access token's ~24h lifetime was the real session
     * length no matter what the user ticked — the refresh cookie (valid for
     * the server's rememberMeExpirationDays, 90 by default) was thrown away on
     * every process death.
     */
    var refreshCookie: String?
        get() = secrets.getString(KEY_REFRESH, null)
        set(value) = secrets.edit().putString(KEY_REFRESH, value).apply()

    /**
     * Sign-out / expiry: drop the access token AND the remember-me cookie.
     * The server URL survives, so this lands on Login rather than Connect.
     */
    fun clearSession() {
        secrets.edit().remove(KEY_TOKEN).remove(KEY_REFRESH).apply()
    }

    private fun openOrReset(context: Context): SharedPreferences = try {
        open(context)
    } catch (e: Throwable) {
        Log.w("TokenStore", "Encrypted prefs unreadable — resetting; sign-in required", e)
        wasReset = true
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
        runCatching {
            java.security.KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }
                .deleteEntry(MASTER_KEY_ALIAS)
        }
        // If even a clean create fails, fall back to an in-memory-ish plain
        // store rather than crashing on launch. The session simply will not
        // persist, which is far better than an app that cannot start.
        runCatching { open(context) }.getOrElse {
            Log.e("TokenStore", "Encrypted prefs unavailable entirely — session will not persist", it)
            context.applicationContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_TOKEN = "access_token"
        private const val KEY_SERVER = "server_url"
        private const val KEY_USERNAME = "last_username"
        private const val KEY_REFRESH = "refresh_cookie"
        private const val PREFS_NAME = "renzo_secure"

        /** Connection details — not secret, and must survive a keystore wipe. */
        private const val CONN_PREFS = "renzo_connection"

        /** Last resort so a broken Keystore cannot make the app unlaunchable. */
        private const val FALLBACK_PREFS = "renzo_secure_fallback"

        /** androidx.security's default MasterKey alias. */
        private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"

        private fun open(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
