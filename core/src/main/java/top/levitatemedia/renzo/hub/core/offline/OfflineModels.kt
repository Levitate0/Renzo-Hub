package top.levitatemedia.renzo.hub.core.offline

import kotlinx.serialization.Serializable
import top.levitatemedia.renzo.hub.core.HubTarget

/**
 * What a stored file is, which decides how it is fetched and read back.
 *
 * The split that matters is VIDEO vs everything else: a page or a subtitle is
 * small enough to buffer in memory and fetch several at a time, a video is not.
 */
enum class AssetRole { PAGE, VIDEO, SUBTITLE, COVER }

@Serializable
data class OfflineAsset(
    val role: AssetRole,
    /** Page number, or subtitle track order. 0 for single-asset roles. */
    val index: Int,
    /** Path within the store, not an absolute filesystem path. */
    val relPath: String,
    val mime: String? = null,
    /** Subtitle language label, null elsewhere. */
    val label: String? = null,
)

/** A downloaded chapter (Shiori) or episode (Renzo). */
@Serializable
data class OfflineItem(
    val target: HubTarget,
    val parentId: String,
    /** chapterKey, or "{titleId}:{ep}". Unique within a target. */
    val itemKey: String,
    /** Chapter number or episode number, for ordering. */
    val ordinal: Double,
    val title: String,
    val assets: List<OfflineAsset>,
    val bytes: Long,
    val savedAt: Long,
    /**
     * False while a resumable transfer is still in flight. A large episode can
     * survive several process deaths before it is whole, and a half-written
     * file must never be offered to the player.
     */
    val complete: Boolean = true,
)

/** A series (Shiori) or title (Renzo) that owns downloaded items. */
@Serializable
data class OfflineParent(
    val target: HubTarget,
    val parentId: String,
    val title: String,
    val coverPath: String? = null,
    val description: String? = null,
    val author: String? = null,
)

/**
 * The whole on-device record, one blob.
 *
 * v3 is the Hub's format. v1/v2 were the Shiori client's, keyed `series` and
 * `chapters` with no notion of which app owned them — see [ManifestMigration].
 */
@Serializable
data class DownloadManifest(
    val version: Int = CURRENT_VERSION,
    /** Keyed "{target}/{parentId}". */
    val parents: Map<String, OfflineParent> = emptyMap(),
    /** Keyed "{target}/{itemKey}". */
    val items: Map<String, OfflineItem> = emptyMap(),
) {
    fun itemsFor(target: HubTarget): List<OfflineItem> =
        items.values.filter { it.target == target }

    fun parentsFor(target: HubTarget): List<OfflineParent> =
        parents.values.filter { it.target == target }

    companion object {
        const val CURRENT_VERSION = 3

        fun parentKey(target: HubTarget, parentId: String) = "$target/$parentId"
        fun itemKey(target: HubTarget, itemKey: String) = "$target/$itemKey"
    }
}
