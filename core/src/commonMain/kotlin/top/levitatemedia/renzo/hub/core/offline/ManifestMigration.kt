package top.levitatemedia.renzo.hub.core.offline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import top.levitatemedia.renzo.hub.core.HubTarget

/**
 * Reads the standalone Shiori client's manifest into the Hub's format.
 *
 * Real users have real downloaded chapters recorded in the old shape, so this
 * is not a nicety — getting it wrong strands gigabytes of already-downloaded
 * pages. The old format had no notion of which app owned an entry, because
 * there was only ever one: every migrated entry is therefore [HubTarget.Shiori].
 *
 * Deliberately pure — no Context, no org.json (which is a throwing stub in JVM
 * unit tests) — so it can be tested directly. See ManifestMigrationTest.
 *
 * Old shape:
 * ```
 * { "version": 2,
 *   "series":   { "<seriesId>":   { seriesId, title, description, author, coverPath? } },
 *   "chapters": { "<chapterKey>": { seriesId, chapterKey, chapterNumber, seriesTitle,
 *                                   pageCount, pagePaths: [...], bytes, savedAt } } }
 * ```
 */
object ManifestMigration {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Returns null when [raw] is absent or unreadable — treated as "nothing to migrate". */
    fun fromV2Json(raw: String?): DownloadManifest? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null

        val parents = LinkedHashMap<String, OfflineParent>()
        val items = LinkedHashMap<String, OfflineItem>()

        (root["series"] as? JsonObject)?.forEach { (seriesId, element) ->
            val o = element as? JsonObject ?: return@forEach
            parents[DownloadManifest.parentKey(HubTarget.Shiori, seriesId)] = OfflineParent(
                target = HubTarget.Shiori,
                parentId = seriesId,
                title = o.str("title") ?: seriesId,
                coverPath = o.str("coverPath"),
                description = o.str("description"),
                author = o.str("author"),
            )
        }

        (root["chapters"] as? JsonObject)?.forEach { (chapterKey, element) ->
            val o = element as? JsonObject ?: return@forEach
            val paths = runCatching { o["pagePaths"]?.jsonArray }.getOrNull() ?: return@forEach
            val assets = paths.mapIndexedNotNull { i, p ->
                val rel = runCatching { p.jsonPrimitive.contentOrNull }.getOrNull() ?: return@mapIndexedNotNull null
                OfflineAsset(role = AssetRole.PAGE, index = i, relPath = rel)
            }
            // A chapter with no readable pages was never usable; don't carry it.
            if (assets.isEmpty()) return@forEach

            val seriesId = o.str("seriesId") ?: return@forEach
            items[DownloadManifest.itemKey(HubTarget.Shiori, chapterKey)] = OfflineItem(
                target = HubTarget.Shiori,
                parentId = seriesId,
                itemKey = chapterKey,
                ordinal = o.dbl("chapterNumber") ?: 0.0,
                title = o.str("seriesTitle") ?: seriesId,
                assets = assets,
                bytes = o.lng("bytes") ?: 0L,
                savedAt = o.lng("savedAt") ?: 0L,
                // Anything the old client recorded had finished downloading —
                // it only wrote the entry after the pages were on disk.
                complete = true,
            )
        }

        // Orphaned item: a chapter whose series entry is missing. The old reader
        // dropped these silently; keep them instead, under a stub parent, so a
        // download the user actually made stays reachable.
        items.values.forEach { item ->
            val key = DownloadManifest.parentKey(item.target, item.parentId)
            parents.getOrPut(key) {
                OfflineParent(
                    target = item.target,
                    parentId = item.parentId,
                    title = item.title,
                )
            }
        }

        // Orphaned parent: a series whose chapters were all unusable or already
        // deleted. Carrying it would put an entry in the library that opens to
        // nothing — the old reader skipped these too ("no chapters saved → don't
        // list"), so dropping them here matches what users already saw.
        val referenced = items.values.map { DownloadManifest.parentKey(it.target, it.parentId) }.toSet()
        parents.keys.retainAll(referenced)

        if (items.isEmpty()) return null
        return DownloadManifest(parents = parents, items = items)
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun JsonObject.dbl(key: String): Double? =
        runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    private fun JsonObject.lng(key: String): Long? =
        runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()
}
