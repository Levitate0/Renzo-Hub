package top.levitatemedia.renzo.tv

import android.content.Context
import android.content.SharedPreferences

/**
 * App-local persistence. Renzo TV is a client for a server the USER hosts, so
 * nothing is baked in: the server address, the session cookie, and the
 * device-local viewing preferences all live here.
 */
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("renzo_tv", Context.MODE_PRIVATE)

    /** Normalized base URL ("https://host[:port]", no trailing slash) or null on first run. */
    var serverUrl: String?
        get() = p.getString("serverUrl", null)?.takeIf { it.isNotBlank() }
        set(v) = p.edit().putString("serverUrl", v?.trim()?.trimEnd('/')).commit().let {}

    /** fsa_session cookie VALUE (opaque token). Replayed on every API/media request. */
    var sessionCookie: String?
        get() = p.getString("session", null)?.takeIf { it.isNotBlank() }
        set(v) = p.edit().putString("session", v).commit().let {}

    /**
     * Adult-content ladder, device-local like the web client's localStorage
     * (`renzo:contentLevel`): none < ecchi < erotica < hentai.
     *
     * Defaults to "none" — a fresh install shows nothing adult until the user
     * opts in. Matches the web UI, tv-native and the Shiori half.
     */
    var contentLevel: String
        get() = p.getString("contentLevel", "none") ?: "none"
        set(v) = p.edit().putString("contentLevel", v).apply()

    /**
     * Last signed-in username. Only used to label the UI when the server can't
     * be reached at boot and there is no /me response to name the user with.
     */
    var lastUsername: String?
        get() = p.getString("lastUsername", null)?.takeIf { it.isNotBlank() }
        set(v) = p.edit().putString("lastUsername", v).apply()

    /** Cached ccLang from /me — the fallback when offline of the per-user pref. */
    var ccLang: String
        get() = p.getString("ccLang", "en") ?: "en"
        set(v) = p.edit().putString("ccLang", v).apply()

    /** Theme preset id (web localStorage `renzo-preset`). */
    var themePreset: String
        get() = p.getString("renzo-preset", "renzo") ?: "renzo"
        set(v) = p.edit().putString("renzo-preset", v).apply()

    /** Custom accent as an ARGB int, or 0 for the preset's own accent
     *  (web `renzo-accent` / `renzo-accent-custom`). */
    var themeAccent: Int
        get() = p.getInt("renzo-accent", 0)
        set(v) = p.edit().putInt("renzo-accent", v).apply()

    // ── playback resume ──────────────────────────────────────────────────
    // Device-local, deliberately: the server tracks whole episodes watched
    // (watchedThrough), not a position inside one, so there is nothing to sync
    // to. Works identically for streamed and downloaded episodes.

    /** Saved position for "{titleId}:{ep}", or 0 when there is nothing to resume. */
    fun resumePositionMs(key: String): Long = p.getLong("resume:$key", 0L)

    fun setResumePosition(key: String, ms: Long) {
        p.edit().putLong("resume:$key", ms).apply()
    }

    fun clearResumePosition(key: String) {
        p.edit().remove("resume:$key").apply()
    }

    fun clearSession() { sessionCookie = null }
    fun forgetServer() {
        p.edit().remove("serverUrl").remove("session").remove("lastUsername").commit()
    }
}
