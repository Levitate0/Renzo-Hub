package top.levitatemedia.renzo.hub.core.offline

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.levitatemedia.renzo.hub.core.HubTarget

/**
 * The migration is the one piece of this feature that can destroy something a
 * user already has: real installs hold real downloaded chapters recorded in the
 * old format. These tests are the correctness oracle for that, and they run on
 * the JVM with no device.
 */
class ManifestMigrationTest {

    /** A real-shaped v2 blob, matching what RenzoDownloadService wrote. */
    private val v2 = """
    {
      "version": 2,
      "series": {
        "s1": {
          "seriesId": "s1",
          "title": "Frieren",
          "description": "after the end",
          "author": "Yamada",
          "coverPath": "offline/covers/s1.jpg"
        }
      },
      "chapters": {
        "s1:12": {
          "seriesId": "s1",
          "chapterKey": "s1:12",
          "chapterNumber": 12.5,
          "seriesTitle": "Frieren",
          "pageCount": 3,
          "pagePaths": ["offline/s1_12/0000.jpg","offline/s1_12/0001.jpg","offline/s1_12/0002.webp"],
          "bytes": 91234,
          "savedAt": 1750000000000
        }
      }
    }
    """.trimIndent()

    @Test
    fun `carries chapters across with pages in order`() {
        val m = ManifestMigration.fromV2Json(v2)
        assertNotNull(m)
        val item = m!!.items[DownloadManifest.itemKey(HubTarget.Shiori, "s1:12")]
        assertNotNull(item)
        assertEquals(HubTarget.Shiori, item!!.target)
        assertEquals("s1", item.parentId)
        assertEquals(12.5, item.ordinal, 0.0001)
        assertEquals(91234L, item.bytes)
        assertEquals(1750000000000L, item.savedAt)
        assertTrue("migrated entries were already downloaded", item.complete)

        assertEquals(3, item.assets.size)
        assertTrue(item.assets.all { it.role == AssetRole.PAGE })
        // Page order is the reading order — getting this wrong scrambles a chapter.
        assertEquals(listOf(0, 1, 2), item.assets.map { it.index })
        assertEquals("offline/s1_12/0000.jpg", item.assets[0].relPath)
        assertEquals("offline/s1_12/0002.webp", item.assets[2].relPath)
    }

    @Test
    fun `carries series metadata and cover`() {
        val m = ManifestMigration.fromV2Json(v2)!!
        val parent = m.parents[DownloadManifest.parentKey(HubTarget.Shiori, "s1")]
        assertNotNull(parent)
        assertEquals("Frieren", parent!!.title)
        assertEquals("offline/covers/s1.jpg", parent.coverPath)
        assertEquals("Yamada", parent.author)
        assertEquals(HubTarget.Shiori, parent.target)
    }

    @Test
    fun `relPaths are left untouched so existing files still resolve`() {
        // The files are already on disk at these paths. Rewriting them would
        // orphan gigabytes; the store resolves old paths unchanged.
        val m = ManifestMigration.fromV2Json(v2)!!
        val paths = m.items.values.flatMap { it.assets }.map { it.relPath }
        assertTrue(paths.all { it.startsWith("offline/") })
    }

    @Test
    fun `keeps a chapter whose series entry is missing`() {
        val orphan = """
        { "version":2, "series":{}, "chapters":{
          "s9:1": { "seriesId":"s9", "chapterKey":"s9:1", "chapterNumber":1.0,
                    "seriesTitle":"Ghost", "pagePaths":["offline/s9_1/0000.jpg"],
                    "bytes":10, "savedAt":1 } } }
        """.trimIndent()
        val m = ManifestMigration.fromV2Json(orphan)!!
        assertEquals(1, m.items.size)
        // A stub parent is synthesised so the download stays reachable.
        val parent = m.parents[DownloadManifest.parentKey(HubTarget.Shiori, "s9")]
        assertNotNull(parent)
        assertEquals("Ghost", parent!!.title)
    }

    @Test
    fun `drops a chapter with no pages`() {
        val empty = """
        { "version":2, "series":{"s1":{"seriesId":"s1","title":"T"}}, "chapters":{
          "s1:1": { "seriesId":"s1", "chapterKey":"s1:1", "pagePaths":[] } } }
        """.trimIndent()
        val m = ManifestMigration.fromV2Json(empty)
        // No usable chapters and therefore nothing worth adopting.
        assertNull(m)
    }

    @Test
    fun `survives junk without throwing`() {
        assertNull(ManifestMigration.fromV2Json(null))
        assertNull(ManifestMigration.fromV2Json(""))
        assertNull(ManifestMigration.fromV2Json("not json at all"))
        assertNull(ManifestMigration.fromV2Json("[1,2,3]"))
        assertNull(ManifestMigration.fromV2Json("""{"version":2}"""))
    }

    @Test
    fun `tolerates missing optional fields`() {
        val sparse = """
        { "chapters": { "k": { "seriesId":"s", "pagePaths":["offline/k/0000.jpg"] } } }
        """.trimIndent()
        val item = ManifestMigration.fromV2Json(sparse)!!.items.values.single()
        assertEquals(0.0, item.ordinal, 0.0001)
        assertEquals(0L, item.bytes)
        assertEquals("s", item.title) // falls back to the series id
    }

    @Test
    fun `round-trips through serialization`() {
        val m = ManifestMigration.fromV2Json(v2)!!
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val back = json.decodeFromString<DownloadManifest>(json.encodeToString(m))
        assertEquals(DownloadManifest.CURRENT_VERSION, back.version)
        assertEquals(m.items, back.items)
        assertEquals(m.parents, back.parents)
    }

    @Test
    fun `library filtering keeps the two halves apart`() {
        val m = ManifestMigration.fromV2Json(v2)!!
        assertEquals(1, m.itemsFor(HubTarget.Shiori).size)
        assertTrue(m.itemsFor(HubTarget.Renzo).isEmpty())
        assertTrue(m.parentsFor(HubTarget.Renzo).isEmpty())
    }
}
