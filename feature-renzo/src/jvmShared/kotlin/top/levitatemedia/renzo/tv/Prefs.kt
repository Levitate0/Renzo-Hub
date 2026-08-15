package top.levitatemedia.renzo.tv

import top.levitatemedia.renzo.hub.core.KeyValuePrefs
import top.levitatemedia.renzo.hub.core.keyValuePrefs

/**
 * App-local persistence. Renzo TV is a client for a server the USER hosts, so
 * nothing is baked in: the server address, the session cookie, and the
 * device-local viewing preferences all live here.
 *
 * Backed by the Hub's KeyValuePrefs seam under the SAME store name the
 * Android SharedPreferences file always used ("renzo_tv"), so existing
 * installs keep every value; the desktop reads/writes a .properties twin.
 */
class Prefs {
    private val p: KeyValuePrefs = keyValuePrefs("renzo_tv")

    /** Normalized base URL ("https://host[:port]", no trailing slash) or null on first run. */
    var serverUrl: String?
        get() = p.getString("serverUrl", null)?.takeIf { it.isNotBlank() }
        set(v) = p.putString("serverUrl", v?.trim()?.trimEnd('/'))

    /** fsa_session cookie VALUE (opaque token). Replayed on every API/media request. */
    var sessionCookie: String?
        get() = p.getString("session", null)?.takeIf { it.isNotBlank() }
        set(v) = p.putString("session", v)

    /**
     * Adult-content ladder, device-local like the web client's localStorage
     * (`renzo:contentLevel`): none < ecchi < erotica < hentai.
     *
     * Defaults to "none" — a fresh install shows nothing adult until the user
     * opts in. Matches the web UI, tv-native and the Shiori half.
     */
    var contentLevel: String
        get() = p.getString("contentLevel", "none") ?: "none"
        set(v) = p.putString("contentLevel", v)

    /**
     * Last signed-in username. Only used to label the UI when the server can't
     * be reached at boot and there is no /me response to name the user with.
     */
    var lastUsername: String?
        get() = p.getString("lastUsername", null)?.takeIf { it.isNotBlank() }
        set(v) = p.putString("lastUsername", v)

    /** Cached ccLang from /me — the fallback when offline of the per-user pref. */
    var ccLang: String
        get() = p.getString("ccLang", "en") ?: "en"
        set(v) = p.putString("ccLang", v)

    /** Theme preset id (web localStorage `renzo-preset`). */
    var themePreset: String
        get() = p.getString("renzo-preset", "renzo") ?: "renzo"
        set(v) = p.putString("renzo-preset", v)

    /** Custom accent as an ARGB int, or 0 for the preset's own accent
     *  (web `renzo-accent` / `renzo-accent-custom`). */
    var themeAccent: Int
        get() = p.getInt("renzo-accent", 0)
        set(v) = p.putInt("renzo-accent", v)

    // ── playback resume ──────────────────────────────────────────────────
    // Device-local, deliberately: the server tracks whole episodes watched
    // (watchedThrough), not a position inside one, so there is nothing to sync
    // to. Works identically for streamed and downloaded episodes.

    /** Saved position for "{titleId}:{ep}", or 0 when there is nothing to resume. */
    fun resumePositionMs(key: String): Long = p.getLong("resume:$key", 0L)

    fun setResumePosition(key: String, ms: Long) {
        p.putLong("resume:$key", ms)
    }

    fun clearResumePosition(key: String) {
        p.remove("resume:$key")
    }

    /** Desktop: explicit external player executable ("" / null = auto-detect). */
    var externalPlayerPath: String?
        get() = p.getString("externalPlayerPath", null)?.takeIf { it.isNotBlank() }
        set(v) = p.putString("externalPlayerPath", v)

    fun clearSession() { sessionCookie = null }
    fun forgetServer() {
        p.remove("serverUrl"); p.remove("session"); p.remove("lastUsername")
    }
}
