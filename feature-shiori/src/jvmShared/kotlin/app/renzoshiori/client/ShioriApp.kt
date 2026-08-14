package app.renzoshiori.client

import app.renzoshiori.client.data.auth.TokenStore
import app.renzoshiori.client.data.network.NetworkModule
import app.renzoshiori.client.data.offline.OfflineRepository
import top.levitatemedia.renzo.hub.core.offline.HubOfflineFiles

/**
 * The services every Shiori screen reaches for. On Android this is RenzoApp
 * (the Application); on desktop it is a plain object built at startup. This
 * interface is what replaced the 36 `applicationContext as RenzoApp` casts —
 * screens in commonMain hold no Context, so they resolve services through
 * [ShioriRuntime] instead.
 */
interface ShioriApp {
    val tokenStore: TokenStore
    val network: NetworkModule
    val offlineStore: HubOfflineFiles
    val offline: OfflineRepository
}

/**
 * Process-wide service locator, set exactly once by each platform's bootstrap
 * BEFORE any Shiori UI composes (RenzoApp.onCreate / desktop main()).
 */
object ShioriRuntime {
    lateinit var app: ShioriApp
}
