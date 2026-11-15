package app.renzoshiori.client.data.auth

import top.levitatemedia.renzo.hub.core.SecretValue
import top.levitatemedia.renzo.hub.core.keyValuePrefs
import top.levitatemedia.renzo.hub.core.secretPrefs

/**
 * Desktop [TokenStore], in the OS config dir (%APPDATA%\RenzoHub on Windows).
 *
 * Connection details stay in the plain "shiori_session" store; the access
 * token and the remember-me cookie live in the secret store — DPAPI-sealed on
 * Windows, an owner-only file elsewhere (see secretPrefs). They used to sit
 * in "shiori_session" as plain text; [SecretValue] lifts them out on first
 * read, so an upgrade keeps the sign-in. The desktop store cannot be
 * invalidated the way the Android Keystore can, so [wasReset] is always false.
 */
class DesktopTokenStore : TokenStore {
    private val prefs = keyValuePrefs("shiori_session")
    private val secrets = secretPrefs("shiori_session_secure")
    private val token = SecretValue(secrets, prefs, KEY_TOKEN)
    private val refresh = SecretValue(secrets, prefs, KEY_REFRESH)

    override val wasReset: Boolean = false

    override var accessToken: String?
        get() = token.get()
        set(value) = token.set(value)

    override var serverUrl: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) = prefs.putString(KEY_SERVER, value)

    override var lastUsername: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.putString(KEY_USERNAME, value)

    override var refreshCookie: String?
        get() = refresh.get()
        set(value) = refresh.set(value)

    override fun clearSession() {
        token.set(null)
        refresh.set(null)
    }

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_SERVER = "server_url"
        const val KEY_USERNAME = "last_username"
        const val KEY_REFRESH = "refresh_cookie"
    }
}
