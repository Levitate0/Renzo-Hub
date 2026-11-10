package app.renzoshiori.client.data.notify

import app.renzoshiori.client.ShioriApp
import app.renzoshiori.client.data.model.SourceHealthDto
import app.renzoshiori.client.data.network.SourcesApi
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.notify.ReleaseAlerts

/**
 * The dead-source alert, for the background check beside the new-chapter one:
 * sources that have failed on the user's series all day without serving a
 * chapter (GET /api/sources/health/alerts, Manager+). Web twin: the command
 * bar's SourceAlert (components/comp/layout/source-alert.tsx).
 *
 * It rides on the Shiori chapter alerts being switched on: that is the user
 * asking this device to tell them about their library.
 */
object ShioriSourceAlertCheck {
    /** Throws when the server can't be reached; the caller tries again later. Empty for a non-manager (403). */
    suspend fun run(app: ShioriApp): List<SourceHealthDto> {
        if (!ReleaseAlerts.anyOn(HubTarget.Shiori)) return emptyList()
        val store = app.tokenStore
        if (store.serverUrl == null || (store.accessToken == null && store.refreshCookie == null)) return emptyList()
        val api = app.network.currentServiceOf<SourcesApi>() ?: return emptyList()
        return try {
            api.sourceHealthAlerts()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) emptyList() else throw e
        }
    }
}
