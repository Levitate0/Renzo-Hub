package top.levitatemedia.renzo.hub.core.offline

/**
 * "Download over mobile data" — one device-wide switch for both halves.
 *
 * Android's downloader (HubDownloadService) waits for an unmetered network
 * unless this is on; the desktop has no metered gate, so it never asks. Off by
 * default: a 3 GB episode over cellular is not something to do by accident.
 * Stored in the shared offline KV under the key the service has always read
 * ("off" = Wi-Fi-only off), so nothing needs migrating.
 */
object DownloadNetworkPrefs {
    const val KEY_WIFI_ONLY = "downloads.wifiOnly"

    fun allowMobileData(store: HubOfflineFiles): Boolean = store.kvGet(KEY_WIFI_ONLY) == "off"

    /**
     * Turning it on also restarts a queue that stopped for want of Wi-Fi, so
     * the switch takes effect at once rather than on the next enqueue.
     */
    fun setAllowMobileData(store: HubOfflineFiles, allow: Boolean) {
        store.kvSet(KEY_WIFI_ONLY, if (allow) "off" else "on")
        if (allow && !DownloadQueue().isEmpty()) HubDownloads.start()
    }
}
