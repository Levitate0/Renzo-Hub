package app.renzoshiori.client

import app.renzoshiori.client.data.auth.DesktopTokenStore
import app.renzoshiori.client.data.auth.TokenStore
import app.renzoshiori.client.data.network.NetworkModule
import app.renzoshiori.client.data.offline.OfflineRepository
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
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
        HubSession.register(HubTarget.Shiori) { builder ->
            val token = tokenStore.accessToken
            if (token != null) {
                builder.header("Authorization", "Bearer $token")
            } else {
                tokenStore.lastUsername?.takeIf { it.isNotBlank() }
                    ?.let { builder.header("X-Renzo-User", it) }
            }
        }
        HubSession.setActive(HubTarget.Shiori)
        DownloadSources.register(HubTarget.Shiori, StaticDownloadSource)

        // The process-wide image loader, authenticating exactly like the REST
        // client (RenzoApp.newImageLoader's desktop twin).
        SingletonImageLoader.setSafe { context ->
            val authedClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val token = tokenStore.accessToken
                    val req = chain.request().newBuilder().apply {
                        if (token != null) addHeader("Authorization", "Bearer $token")
                    }.build()
                    chain.proceed(req)
                }
                .build()
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { authedClient })) }
                .build()
        }
    }
}
