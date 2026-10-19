package app.renzoshiori.client.data.network

import app.renzoshiori.client.ShioriApp
import okhttp3.Request
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.RequestSigner

/**
 * Registers this half with [HubSession], so the clients the two halves share
 * (the image loader and the downloader) can sign for it.
 *
 * One definition for both platforms: Android's and the desktop's copies had
 * already drifted once. [app]'s stores are read at request time, never here:
 * on Android they open lazily, and the Keystore-backed one may not be
 * openable yet when the Application starts before the device is unlocked.
 */
fun registerShioriSigner(app: ShioriApp) {
    HubSession.register(
        HubTarget.Shiori,
        server = { app.tokenStore.serverUrl },
        signer = object : RequestSigner {
            override fun sign(builder: Request.Builder) {
                val tokenStore = app.tokenStore
                val token = tokenStore.accessToken
                if (token != null) {
                    builder.header("Authorization", "Bearer $token")
                } else {
                    tokenStore.lastUsername?.takeIf { it.isNotBlank() }
                        ?.let { builder.header("X-Renzo-User", it) }
                }
            }

            // The access token lasts a day, and a download queue or a screen of
            // covers outlives it. Renew through the half's own client, which
            // keeps the rotated refresh cookie.
            override fun renew(rejected: Request): Boolean = app.network.renewSession(rejected)
        },
    )
}
