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
class AndroidTokenStore(context: Context) : TokenStore {
    /** True when the encrypted store had to be thrown away on this launch. */
    override var wasReset: Boolean = false
        private set

    private val appContext: Context = context.applicationContext

    /** The encrypted store, once it has genuinely opened. */
    @Volatile private var realSecrets: SharedPreferences? = null

    /**
     * Stand-in used only while the Keystore is locked (Direct Boot). Separate
     * from [realSecrets] so [secrets] can tell "opened" from "standing in".
     */
    @Volatile private var lockedFallback: SharedPreferences? = null

    private val secretsLock = Any()

    /**
     * The secrets store, reopening the encrypted one if a previous call had to
     * stand in for it.
     *
     * This used to be a `val` resolved once in the constructor, and that is the
     * "Renzo Hub forgot my Shiori login again" report. The store is created
     * from the Application, lazily, so the FIRST thing to touch it after a
     * reboot is usually the download queue resuming headlessly — before the
     * user has unlocked the device. The Keystore is unavailable then, so the
     * pre-unlock branch handed back an EMPTY plain store... and the `val`
     * pinned it there for the entire process lifetime.
     *
     * Everything downstream followed from that: reads returned no token and no
     * refresh cookie, so AuthViewModel saw a server with no credential at all
     * and landed on Login; and any token obtained afterwards was WRITTEN to the
     * stand-in, where the next launch would never look for it. Renzo is immune
     * because its settings are plain — no Keystore, nothing to be locked out
     * of.
     *
     * Reopening on access is enough: the very next call after the user unlocks
     * gets the real store, and anything written meanwhile is migrated into it
     * rather than stranded.
     */
    private fun secrets(): SharedPreferences {
        realSecrets?.let { return it }
        synchronized(secretsLock) {
            realSecrets?.let { return it }

            val opened = openOrNull()
            if (opened != null) {
                lockedFallback?.let { stranded ->
                    migrateStranded(stranded, opened)
                    lockedFallback = null
                }
                realSecrets = opened
                return opened
            }

            // Still locked. Stand in, and try again on the next access.
            return lockedFallback ?: appContext
                .getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
                .also { lockedFallback = it }
        }
    }

    /**
     * Anything written while the Keystore was locked belongs in the real store
     * the moment it opens — otherwise a sign-in that happened during that
     * window is silently thrown away.
     */
    private fun migrateStranded(from: SharedPreferences, to: SharedPreferences) {
        val token = from.getString(KEY_TOKEN, null)
        val refresh = from.getString(KEY_REFRESH, null)
        if (token == null && refresh == null) return
        Log.i("TokenStore", "Moving credentials written before unlock into the encrypted store.")
        @Suppress("ApplySharedPref")
        to.edit().apply {
            token?.let { putString(KEY_TOKEN, it) }
            refresh?.let { putString(KEY_REFRESH, it) }
        }.commit()
        @Suppress("ApplySharedPref")
        from.edit().clear().commit()
    }

    /** Not secret, and deliberately outside the store that can be wiped. */
    private val conn: SharedPreferences =
        context.applicationContext.getSharedPreferences(CONN_PREFS, Context.MODE_PRIVATE)

    init {
        // One-time lift of values written before the split, so existing installs
        // keep their server without a re-Connect.
        if (!conn.contains(KEY_SERVER)) {
            secrets().getString(KEY_SERVER, null)?.let { conn.edit().putString(KEY_SERVER, it).apply() }
        }
        if (!conn.contains(KEY_USERNAME)) {
            secrets().getString(KEY_USERNAME, null)?.let { conn.edit().putString(KEY_USERNAME, it).apply() }
        }
    }

    override var accessToken: String?
        get() = secrets().getString(KEY_TOKEN, null)
        // commit(), for the same reason serverUrl uses it: these are written
        // once at sign-in, and an apply() still queued when the process is
        // killed is a sign-in the user has to repeat.
        @Suppress("ApplySharedPref")
        set(value) { secrets().edit().putString(KEY_TOKEN, value).commit() }

    override var serverUrl: String?
        get() = conn.getString(KEY_SERVER, null)
        // commit(), not apply(): losing this to a process death is what makes
        // the app look like it forgot the server.
        set(value) { conn.edit().putString(KEY_SERVER, value).commit() }

    override var lastUsername: String?
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
    override var refreshCookie: String?
        get() = secrets().getString(KEY_REFRESH, null)
        @Suppress("ApplySharedPref")
        set(value) { secrets().edit().putString(KEY_REFRESH, value).commit() }

    /**
     * Sign-out / expiry: drop the access token AND the remember-me cookie.
     * The server URL survives, so this lands on Login rather than Connect.
     */
    @Suppress("ApplySharedPref")
    override fun clearSession() {
        secrets().edit().remove(KEY_TOKEN).remove(KEY_REFRESH).commit()
    }

    /**
     * Opens the encrypted store, or returns NULL when the Keystore is merely
     * locked and the right answer is "ask again later".
     *
     * Returning null rather than a stand-in is the whole point: the caller can
     * then retry, where the old signature could only hand back something to be
     * held forever.
     */
    private fun openOrNull(): SharedPreferences? {
        val context = appContext
        try {
            return open(context)
        } catch (first: Throwable) {
            // NOT every failure means the store is corrupt, and the old code
            // treated them all that way — deleting the prefs file and the
            // master key on any Throwable at all.
            //
            // The Keystore is simply UNAVAILABLE while the device has not been
            // unlocked since boot, and this class is constructed from the
            // Application: a background start after a reboot (the download
            // queue resuming, by design) lands here with credentials that are
            // perfectly intact, and wiped them. That is the "sometimes signed
            // out" report — it tracks reboots, not anything the user did.
            if (!userUnlocked(context)) {
                Log.w("TokenStore", "Keystore locked (pre-unlock boot) — NOT resetting; will reopen once unlocked", first)
                return null
            }
            // One retry: EncryptedSharedPreferences.create can also lose a race
            // with itself when two components open the same file at once.
            runCatching { open(context) }.onSuccess { return it }
            return reset(context, first)
        }
    }

    /** True unless the device is still locked after a reboot (Direct Boot). */
    private fun userUnlocked(context: Context): Boolean =
        runCatching {
            context.applicationContext.getSystemService(android.os.UserManager::class.java)?.isUserUnlocked
        }.getOrNull() ?: true

    private fun reset(context: Context, e: Throwable): SharedPreferences {
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
        return runCatching { open(context) }.getOrElse {
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
