package app.renzoshiori.client.data.auth

/**
 * Credentials and connection details — the platform seam.
 *
 * Android implements this over EncryptedSharedPreferences + plain prefs
 * ([AndroidTokenStore], with its keystore self-healing); the desktop over a
 * file in the OS config dir. The split-store CONTRACT is part of the
 * interface: secrets (token, refresh cookie) may be lost to a platform
 * keystore reset, but [serverUrl] and [lastUsername] must survive it —
 * losing the server address to a credential wipe is what made the app look
 * like a fresh install.
 */
interface TokenStore {
    /** True when the secret store had to be thrown away on this launch. */
    val wasReset: Boolean

    var accessToken: String?
    var serverUrl: String?
    var lastUsername: String?

    /**
     * The server's httpOnly `refresh_token` cookie, persisted so "Remember me"
     * survives the process. See AndroidTokenStore for the full reasoning.
     */
    var refreshCookie: String?

    /**
     * Sign-out / expiry: drop the access token AND the remember-me cookie.
     * The server URL survives, so this lands on Login rather than Connect.
     */
    fun clearSession()
}
