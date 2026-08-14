package top.levitatemedia.renzo.hub.core.offline

import top.levitatemedia.renzo.hub.core.HubTarget

/**
 * Read side of the offline store, scoped to one half of the Hub.
 *
 * Each feature holds its own instance, so neither can see or delete the
 * other's downloads even though they share one manifest and one folder.
 */
class OfflineLibrary(
    val store: HubOfflineFiles,
    private val target: HubTarget,
) {
    /** Parents that actually have at least one complete item. */
    fun listParents(): List<OfflineParent> {
        val m = store.getManifest()
        val withItems = m.itemsFor(target).filter { it.complete }.map { it.parentId }.toSet()
        return m.parentsFor(target).filter { it.parentId in withItems }.sortedBy { it.title.lowercase() }
    }

    fun listItems(parentId: String? = null): List<OfflineItem> =
        store.getManifest().itemsFor(target)
            .filter { it.complete && (parentId == null || it.parentId == parentId) }
            .sortedBy { it.ordinal }

    fun item(itemKey: String): OfflineItem? =
        store.getManifest().items[DownloadManifest.itemKey(target, itemKey)]

    /** True only for a fully-downloaded item — a partial transfer must not count. */
    fun isOffline(itemKey: String): Boolean = item(itemKey)?.complete == true

    fun parent(parentId: String): OfflineParent? =
        store.getManifest().parents[DownloadManifest.parentKey(target, parentId)]

    /** Pages and subtitles. Never video — use [uriOf]. */
    fun readAsset(asset: OfflineAsset): ByteArray? = store.readFile(asset.relPath)

    /** For the player: a streamable URI rather than bytes. */
    fun assetsOf(itemKey: String, role: AssetRole): List<OfflineAsset> =
        item(itemKey)?.assets?.filter { it.role == role }?.sortedBy { it.index } ?: emptyList()

    fun totalBytes(): Long = store.getManifest().itemsFor(target).sumOf { it.bytes }

    /** Removes the files and the manifest entry, and the parent once empty. */
    fun deleteItem(itemKey: String) {
        val key = DownloadManifest.itemKey(target, itemKey)
        val existing = store.getManifest().items[key] ?: return
        existing.assets.forEach { store.deletePath(it.relPath) }
        store.updateManifest { m ->
            val items = m.items - key
            val stillUsed = items.values.any {
                it.target == target && it.parentId == existing.parentId
            }
            m.copy(
                items = items,
                parents = if (stillUsed) m.parents
                else m.parents - DownloadManifest.parentKey(target, existing.parentId),
            )
        }
    }

    fun deleteParent(parentId: String) {
        listItems(parentId).forEach { deleteItem(it.itemKey) }
    }
}
