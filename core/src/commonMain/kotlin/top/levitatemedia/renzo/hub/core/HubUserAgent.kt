package top.levitatemedia.renzo.hub.core

/**
 * How the Hub identifies itself to the servers. The Shiori server derives a
 * session's device label from the login request's User-Agent
 * (AuthController.DeviceNameFromRequest — any UA containing "Renzo" labels
 * the session "Renzo app"); without this, OkHttp's default UA fell through
 * every branch and the Hub showed up in the devices list as "Browser"
 * (user report 2026-08-21). The platform suffix is for server logs.
 */
val HUB_USER_AGENT: String by lazy {
    val platform = when {
        HubPlatform.isDesktop -> "Desktop"
        HubPlatform.isTv -> "Android TV"
        else -> "Android"
    }
    "RenzoHub ($platform)"
}
