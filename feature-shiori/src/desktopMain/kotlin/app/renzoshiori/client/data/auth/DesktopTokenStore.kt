package app.renzoshiori.client.data.auth

import top.levitatemedia.renzo.hub.core.keyValuePrefs

/**
 * Desktop [TokenStore]: connection details AND session credentials in the OS
 * config dir (%APPDATA%\RenzoHub on Windows).
 *
 * The Android split-store exists because the Keystore can invalidate the
 * encrypted half; the desktop has no such failure mode, so one store is
 * enough and [wasReset] is always false.
 *
 * TODO(security): the session file is plain text, protected only by OS file
 * permissions — same posture as the WPF client's WebView2 cookie store this
 * replaces. DPAPI (Windows) / keychain would be strictly better.
 */
class DesktopTokenStore : TokenStore {
    private val prefs = keyValuePrefs("shiori_session")

    override val wasReset: Boolean = false

    override var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.putString(KEY_TOKEN, value)

    override var serverUrl: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) = prefs.putString(KEY_SERVER, value)

    override var lastUsername: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.putString(KEY_USERNAME, value)

    override var refreshCookie: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.putString(KEY_REFRESH, value)

    override fun clearSession() {
        prefs.remove(KEY_TOKEN)
        prefs.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_SERVER = "server_url"
        const val KEY_USERNAME = "last_username"
        const val KEY_REFRESH = "refresh_cookie"
    }
}
