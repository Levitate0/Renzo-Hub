package top.levitatemedia.renzo.hub.core

/**
 * Plain key/value settings — the client's localStorage. Android backs each
 * named store with the SharedPreferences file OF THE SAME NAME (so existing
 * installs keep every setting); the desktop backs it with a .properties file
 * in the OS config dir.
 *
 * Not for secrets — those go in [secretPrefs] (or Shiori's Android TokenStore).
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

    /**
     * Like [putString], but does not return until the value is durable.
     *
     * Android's SharedPreferences.apply() only writes to memory and queues the
     * disk write; a process death before it lands loses it. That is fine for a
     * card size or a resume position, and NOT fine for the server address or
     * the session — losing those is what makes the app look like it forgot the
     * server. The Shiori half already draws this distinction by hand
     * (AndroidTokenStore uses commit() for exactly these two values); this is
     * the same rule available through the shared seam.
     *
     * Defaults to [putString], which is already correct wherever writes are
     * synchronous (the desktop .properties store).
     */
    fun putStringDurable(key: String, value: String?) = putString(key, value)
}

/** Open (or create) the named settings store. */
expect fun keyValuePrefs(name: String): KeyValuePrefs

/**
 * Where session secrets live: encrypted at rest and, on Android, kept out of
 * backups and device transfer (see backup_rules.xml).
 *
 *  * Android: EncryptedSharedPreferences under a Keystore key of its own.
 *  * Windows: a .properties file whose values are DPAPI-sealed to the user.
 *  * Elsewhere: a .properties file readable by its owner only.
 *
 * Every write is durable. [isAvailable] is false while the store cannot be
 * opened — on Android, before the first unlock after a reboot, when a
 * download queue resuming headlessly is often the first thing to ask.
 */
interface SecretPrefs {
    fun isAvailable(): Boolean
    fun getString(key: String): String?

    /** False when the store is unavailable right now; nothing was written. */
    fun putString(key: String, value: String?): Boolean
}

/** Open (or create) the named secret store. One instance per name. */
expect fun secretPrefs(name: String): SecretPrefs

/**
 * One secret, kept in [secrets], with the plain [legacy] store as its past.
 *
 * Older versions kept sessions in plain settings — on Android in a file that
 * Auto Backup and device transfer copy off the device. Reading lifts any value
 * found there into [secrets] and only then deletes the plain copy, so an
 * upgrade keeps the user signed in and a kill between the two steps just
 * repeats the lift.
 *
 * While [secrets] cannot be opened, writes go to [pending]: a plain store that
 * must be excluded from backup and device transfer like [secrets] itself, so
 * a sign-in during that window is kept without putting the session back where
 * backups reach it. A sign-out in that window is written there as "" — a
 * tombstone — so the older encrypted value cannot come back once the store
 * opens. [pending] is always the newest value and is lifted last. Where
 * [secrets] is always available (the desktop) it may simply be [legacy].
 */
class SecretValue(
    private val secrets: SecretPrefs,
    private val legacy: KeyValuePrefs,
    private val key: String,
    private val pending: KeyValuePrefs = legacy,
) {
    fun get(): String? {
        if (!secrets.isAvailable()) {
            val held = pending.getString(key, null) ?: legacy.getString(key, null)
            return held?.takeIf { it.isNotBlank() }
        }
        lift(legacy)
        if (pending !== legacy) lift(pending)
        return secrets.getString(key)?.takeIf { it.isNotBlank() }
    }

    fun set(value: String?) {
        if (secrets.putString(key, value)) {
            clear(legacy)
            if (pending !== legacy) clear(pending)
        } else {
            pending.putStringDurable(key, value ?: "")
            // Older than what was just written; no reason to leave it in a backup.
            if (pending !== legacy) clear(legacy)
        }
    }

    /** Moves a plain value into [secrets]; the plain copy goes only once that write is durable. */
    private fun lift(from: KeyValuePrefs) {
        from.getString(key, null)?.let { plain ->
            if (secrets.putString(key, plain.takeIf { it.isNotBlank() })) from.putStringDurable(key, null)
        }
    }

    private fun clear(from: KeyValuePrefs) {
        if (from.getString(key, null) != null) from.putStringDurable(key, null)
    }
}
