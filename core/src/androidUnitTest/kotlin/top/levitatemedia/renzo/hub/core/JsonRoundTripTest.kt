package top.levitatemedia.renzo.hub.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Shiori add-to-library flow round-trips the augment response back to
 * /api/serie **verbatim** — the server expects every field it sent, including
 * the ones the UI never models. Anything the client silently drops on the way
 * back out is a failed add.
 *
 * The Hub runs kotlinx-serialization 1.9.0 where the standalone client runs
 * 1.8.0, so this pins the behaviour that matters: encoding a raw JsonObject
 * under the app's Json config must not lose keys, nulls, or nesting.
 */
class JsonRoundTripTest {

    /** Byte-for-byte the config in NetworkModule. */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        coerceInputValues = true
        explicitNulls = false
    }

    /** Shaped like an augment response: nulls, empty arrays, nested objects. */
    private val payload = """
    {
      "id": "abc",
      "title": "Backstabbed in a Backwater Dungeon",
      "description": null,
      "author": null,
      "chapterList": [],
      "chapters": [
        {"number": 1.0, "filename": "c1.cbz", "pageCount": null},
        {"number": 2.5, "filename": null, "pageCount": 18}
      ],
      "suggestedFilename": null,
      "meta": {"nested": {"deep": null}, "flag": false},
      "tags": ["action", "isekai"]
    }
    """.trimIndent()

    @Test
    fun `raw JsonObject survives an encode-decode round trip intact`() {
        val decoded = json.decodeFromString<JsonObject>(payload)
        val reEncoded = json.encodeToString(JsonObject.serializer(), decoded)
        val again = json.decodeFromString<JsonObject>(reEncoded)
        assertEquals("re-encoding a raw JsonObject must not change it", decoded, again)
    }

    @Test
    fun `explicitNulls false does not strip nulls from a raw JsonObject`() {
        val decoded = json.decodeFromString<JsonObject>(payload)
        val reEncoded = json.encodeToString(JsonObject.serializer(), decoded)

        // These are the fields the server sent as null and expects back.
        listOf("description", "author", "suggestedFilename").forEach { key ->
            assert(reEncoded.contains("\"$key\"")) {
                "explicitNulls=false dropped '$key' — the server would receive an " +
                    "incomplete payload and reject the add"
            }
        }
        assertEquals(decoded.keys, json.decodeFromString<JsonObject>(reEncoded).keys)
    }

    @Test
    fun `nested nulls and empty arrays survive`() {
        val decoded = json.decodeFromString<JsonObject>(payload)
        val again = json.decodeFromString<JsonObject>(
            json.encodeToString(JsonObject.serializer(), decoded),
        )
        assertEquals(decoded["chapters"], again["chapters"])
        assertEquals(decoded["chapterList"], again["chapterList"])
        assertEquals(decoded["meta"], again["meta"])
    }
}
