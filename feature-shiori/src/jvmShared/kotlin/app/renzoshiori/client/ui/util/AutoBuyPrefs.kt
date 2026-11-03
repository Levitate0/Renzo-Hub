package app.renzoshiori.client.ui.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject

/**
 * Account-wide "auto-buy paid chapters" switch (lib/utils/autobuy-prefs.ts):
 * `autoBuyPaidChapters` in the per-user preferences blob, read server-side by
 * UserPriorityPrefsExtensions.GetAutoBuyPaidChapters. A series can override it.
 * Writes round-trip the whole blob so no other key is lost.
 */
object AutoBuyPrefs {
    private const val KEY = "autoBuyPaidChapters"
    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(prefs: String?): JsonObject? =
        if (prefs.isNullOrBlank()) null else runCatching { json.parseToJsonElement(prefs) as? JsonObject }.getOrNull()

    fun parse(prefs: String?): Boolean = (obj(prefs)?.get(KEY) as? JsonPrimitive)?.booleanOrNull ?: false

    fun merge(prefs: String?, enabled: Boolean): String = buildJsonObject {
        obj(prefs)?.forEach { (k, v) -> if (k != KEY) put(k, v) }
        put(KEY, JsonPrimitive(enabled))
    }.toString()
}
