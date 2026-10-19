package app.renzoshiori.client

import app.renzoshiori.client.data.auth.DesktopTokenStore
import app.renzoshiori.client.data.auth.TokenStore
import app.renzoshiori.client.data.network.NetworkModule
import app.renzoshiori.client.data.offline.OfflineRepository
import coil3.SingletonImageLoader
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.offline.DesktopOfflineFiles
import top.levitatemedia.renzo.hub.core.offline.DownloadSources
import top.levitatemedia.renzo.hub.core.offline.HubOfflineFiles
import top.levitatemedia.renzo.hub.core.offline.StaticDownloadSource

/**
 * The desktop's RenzoApp: same composition root, no Application class. Called
 * once from hub-desktop's main() BEFORE any Shiori UI composes — the mirror
 * of RenzoApp.onCreate on Android.
 */
object ShioriDesktop {
    fun install(debugHttp: Boolean = false) {
        if (ShioriRuntime.installed) return

        val tokenStore: TokenStore = DesktopTokenStore()
        val store: HubOfflineFiles = DesktopOfflineFiles()
        val services = object : ShioriApp {
            override val tokenStore: TokenStore = tokenStore
            override val network: NetworkModule = NetworkModule(tokenStore)
            override val offlineStore: HubOfflineFiles = store
            override val offline: OfflineRepository = OfflineRepository(store)
        }
        ShioriRuntime.app = services
        NetworkModule.logBodies = debugHttp
        app.renzoshiori.client.ui.util.AdultFilter.init()

        // Same signer contract as Android: the downloader and the shared Coil
        // loader sign with whatever credentials this half currently holds.
        app.renzoshiori.client.data.network.registerShioriSigner(services)
        HubSession.setActive(HubTarget.Shiori)
        DownloadSources.register(HubTarget.Shiori, StaticDownloadSource)

        // The process-wide image loader (RenzoApp.newImageLoader's desktop
        // twin): the Hub's shared one, which signs each request for the half
        // whose server it is going to and nobody else.
        SingletonImageLoader.setSafe { context -> top.levitatemedia.renzo.hub.core.HubImageLoader.build(context) }
    }
}
