package app.renzoshiori.client.data.offline

import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.offline.AssetRole
import top.levitatemedia.renzo.hub.core.offline.OfflineLibrary
import top.levitatemedia.renzo.hub.core.offline.HubOfflineFiles

/**
 * Shiori's read-model over the offline manifest.
 *
 * The storage moved into :core (shared with the anime half) but this API did
 * not: the same data classes, the same method names, the same ordering. Eight
 * screens read through it and none of them needed to change.
 *
 * The manifest itself migrates from the standalone client's v2 format on first
 * read — see ManifestMigration — so existing downloads carry over untouched.
 */
class OfflineRepository(private val store: HubOfflineFiles) {
    private val library = OfflineLibrary(store, HubTarget.Shiori)

    data class OfflineSeries(
        val seriesId: String,
        val title: String,
        val coverPath: String?,
        val chapterCount: Int,
        val bytes: Long,
    )

    data class OfflineChapter(
        val seriesId: String,
        val chapterKey: String,
        val chapterNumber: Double,
        val seriesTitle: String,
        val pageCount: Int,
        val pagePaths: List<String>,
        val bytes: Long,
        val savedAt: Long,
    )

    fun listSeries(): List<OfflineSeries> {
        val items = library.listItems()
        return library.listParents().mapNotNull { parent ->
            val mine = items.filter { it.parentId == parent.parentId }
            // A series with nothing saved is not listed, as before.
            if (mine.isEmpty()) return@mapNotNull null
            OfflineSeries(
                seriesId = parent.parentId,
                title = parent.title,
                coverPath = parent.coverPath,
                chapterCount = mine.size,
                bytes = mine.sumOf { it.bytes },
            )
        }
    }

    fun listChapters(seriesId: String? = null): List<OfflineChapter> =
        library.listItems(seriesId).map { item ->
            val pages = item.assets.filter { it.role == AssetRole.PAGE }.sortedBy { it.index }
            OfflineChapter(
                seriesId = item.parentId,
                chapterKey = item.itemKey,
                chapterNumber = item.ordinal,
                seriesTitle = item.title,
                pageCount = pages.size,
                pagePaths = pages.map { it.relPath },
                bytes = item.bytes,
                savedAt = item.savedAt,
            )
        }

    fun isChapterOffline(chapterKey: String): Boolean = library.isOffline(chapterKey)

    /** Raw page bytes for a saved page (Coil can display via ByteArray). */
    fun readPage(relPath: String): ByteArray? = store.readFile(relPath)

    fun deleteChapter(chapterKey: String) = library.deleteItem(chapterKey)
}
