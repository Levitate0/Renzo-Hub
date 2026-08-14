package top.levitatemedia.renzo.hub.core.offline

import kotlinx.serialization.Serializable
import top.levitatemedia.renzo.hub.core.HubTarget

/** One remote file to fetch. [url] may be server-relative or absolute. */
@Serializable
data class PendingAsset(
    val role: AssetRole,
    val index: Int,
    val url: String,
    val label: String? = null,
)

@Serializable
data class PendingItem(
    val itemKey: String,
    val ordinal: Double,
    val title: String,
    /**
     * Null when the assets must be resolved at fetch time rather than at
     * enqueue time. Renzo needs that — its playback URLs are time-limited
     * debrid links, so a job queued at 09:00 and reached at 11:00 would carry
     * a dead URL. Shiori's page paths are stable server routes and can be
     * baked in here.
     */
    val assets: List<PendingAsset>? = null,
)

@Serializable
data class DownloadJob(
    val target: HubTarget,
    val baseUrl: String,
    val parentId: String,
    val parentTitle: String,
    val parentDescription: String? = null,
    val parentAuthor: String? = null,
    /** Source URL for the cover, fetched once per parent. */
    val coverUrl: String? = null,
    val items: List<PendingItem>,
)
