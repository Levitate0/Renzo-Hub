package app.renzoshiori.client.data.offline

import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.ReaderMarkRequestDto
import app.renzoshiori.client.data.model.ReaderProgressRequestDto
import app.renzoshiori.client.data.network.ApiService
import app.renzoshiori.client.ui.series.chapterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import top.levitatemedia.renzo.hub.core.HubPlatform

/**
 * What the Hub does about being offline once it is online again — the native
 * twin of the web shell's `lib/native/read-sync.ts` (flush offline reads) and
 * `useReconnectPurge` in `lib/native/hooks.ts` (ask to clean up on reconnect).
 *
 * **Reading progress.** The reader writes progress straight to the server;
 * when that write cannot reach it (a saved chapter read on a train), the write
 * is queued here instead of dropped. The web queue only carries "finished"
 * marks; this one carries the page too, last write per chapter winning. The
 * flush never makes a chapter LESS read than the server already has it: the
 * server's progress endpoint moves both ways (scrolling back lowers it), so
 * each queued entry is compared with the server's current state first and
 * only pushed when it is further along, and a chapter the server already has
 * as completed is left alone.
 *
 * **Reconnect clean-up** ("Auto-clean on reconnect", Downloads). After an
 * offline spell, once the server answers again, it ASKS before removing
 * anything (a signal blip must not wipe trip downloads — the web's rule) and,
 * deliberately more conservative than the web (which offers to remove every
 * download), it only offers chapters the server says are already read. The
 * chapter open in the reader is always kept.
 */
object OfflineSync {
    /** Shared with DownloadsScreen's switch; the web's own key name. */
    const val AUTO_PURGE_KEY = "renzo.offline.autopurge"
    private const val PENDING_KEY = "renzo.offline.pendingProgress"
    /** The web shell's queue ({seriesId, chapterNumber} = finished). Drained too. */
    private const val LEGACY_PENDING_KEY = "renzo.offline.pendingReads"
    /** Set while the server can't be reached; survives a restart (web Renzo's OFFLINE_FLAG). */
    private const val WAS_OFFLINE_KEY = "renzo.offline.wasOffline"

    @Serializable
    private data class Pending(
        val seriesId: String,
        val chapterNumber: Double,
        val lastReadPage: Int = 0,
        val totalPages: Int = 0,
        val filename: String? = null,
        val completed: Boolean = false,
    )

    @Serializable
    private data class LegacyPending(val seriesId: String = "", val chapterNumber: Double? = null)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val flushLock = Mutex()
    private val kvLock = Any()

    /** The chapter the reader has open (web current-read.ts) — never purged. */
    @Volatile var currentReadingKey: String? = null

    private val _purgePrompt = MutableStateFlow<Int?>(null)
    /** Non-null = "remove N read downloads?" is waiting for an answer. */
    val purgePrompt: StateFlow<Int?> = _purgePrompt.asStateFlow()
    private var purgeKeys: List<String> = emptyList()

    private val _revision = MutableStateFlow(0)
    /** Bumped when the clean-up removed downloads, so lists can reload. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val store get() = ShioriRuntime.app.offlineStore

    fun autoPurgeEnabled(): Boolean = store.kvGet(AUTO_PURGE_KEY) != "off"

    // ── queue ────────────────────────────────────────────────────────────────

    private fun readPending(): List<Pending> =
        store.kvGet(PENDING_KEY)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(ListSerializer(Pending.serializer()), it) }.getOrNull() }
            ?: emptyList()

    private fun writePending(list: List<Pending>) =
        store.kvSet(PENDING_KEY, json.encodeToString(ListSerializer(Pending.serializer()), list))

    /**
     * Queue a progress write the server didn't get. Last write per chapter wins
     * for the page; "completed" is sticky, as it is on the server.
     */
    fun record(
        seriesId: String,
        chapterNumber: Double,
        lastReadPage: Int,
        totalPages: Int,
        filename: String?,
        completed: Boolean,
    ) {
        synchronized(kvLock) {
            val list = readPending()
            val old = list.firstOrNull { it.seriesId == seriesId && it.chapterNumber == chapterNumber }
            val entry = Pending(
                seriesId, chapterNumber, lastReadPage, totalPages,
                filename ?: old?.filename, completed || old?.completed == true,
            )
            writePending(list.filterNot { it === old } + entry)
        }
        markOffline()
    }

    /** A live write for this chapter reached the server: it supersedes the queued one. */
    fun forget(seriesId: String, chapterNumber: Double) {
        synchronized(kvLock) {
            val list = readPending()
            if (list.any { it.seriesId == seriesId && it.chapterNumber == chapterNumber }) {
                writePending(list.filterNot { it.seriesId == seriesId && it.chapterNumber == chapterNumber })
            }
        }
    }

    fun markOffline() = store.kvSet(WAS_OFFLINE_KEY, "1")

    private fun wasOffline(): Boolean = store.kvGet(WAS_OFFLINE_KEY) == "1"

    private fun hasPending(): Boolean =
        readPending().isNotEmpty() || !store.kvGet(LEGACY_PENDING_KEY).isNullOrBlank()

    // ── the reconnect step ───────────────────────────────────────────────────

    /**
     * One look at the connection; the watcher calls it every few seconds while
     * the Shiori half is up (and once at start — the web flushes on mount).
     * Cheap when there is nothing to do: no request at all.
     */
    suspend fun tick() {
        val api = ShioriRuntime.app.network.currentApi() ?: return
        if (!HubPlatform.hasInternet()) {
            markOffline()
            return
        }
        val was = wasOffline()
        if (!was && !hasPending()) return
        if (runCatching { api.me() }.isFailure) {
            markOffline()
            return
        }
        if (!flush(api)) return
        if (was) {
            store.kvSet(WAS_OFFLINE_KEY, "")
            if (autoPurgeEnabled() && _purgePrompt.value == null) offerPurge(api)
        }
    }

    /** Push what was queued. False = the server stopped answering part-way. */
    private suspend fun flush(api: ApiService): Boolean = flushLock.withLock {
        val legacy = store.kvGet(LEGACY_PENDING_KEY)?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(LegacyPending.serializer()), raw) }.getOrNull()
        }.orEmpty().mapNotNull { l ->
            val n = l.chapterNumber ?: return@mapNotNull null
            if (l.seriesId.isBlank()) null else Pending(l.seriesId, n, completed = true)
        }
        val snapshot = readPending()
        val work = (snapshot + legacy)
        if (work.isEmpty()) {
            store.kvSet(LEGACY_PENDING_KEY, "")
            return@withLock true
        }

        val done = HashSet<Pending>()
        var reachable = true
        for ((seriesId, entries) in work.groupBy { it.seriesId }) {
            val server = try {
                api.readerChapters(seriesId)
            } catch (e: retrofit2.HttpException) {
                // The series is gone or no longer this user's: nothing to sync to.
                if (e.code() in 400..499 && e.code() != 401 && e.code() != 408 && e.code() != 429) done += entries
                continue
            } catch (_: Exception) {
                reachable = false
                break
            }
            for (e in entries) {
                val sc = server.chapters.firstOrNull { it.number == e.chapterNumber }
                if (sc == null || sc.isCompleted) {
                    // Unknown chapter, or already finished there — never un-finish.
                    done += e
                    continue
                }
                try {
                    if (e.completed) {
                        api.markChapters(ReaderMarkRequestDto(seriesId, listOf(e.chapterNumber), true))
                    } else if (e.totalPages > 0 &&
                        e.lastReadPage.toFloat() / e.totalPages > sc.progress + 0.0001f
                    ) {
                        api.setProgress(
                            ReaderProgressRequestDto(
                                seriesId = seriesId,
                                chapterNumber = e.chapterNumber,
                                lastReadPage = e.lastReadPage,
                                totalPages = e.totalPages,
                                filename = e.filename,
                            ),
                        )
                    }
                    done += e
                } catch (ex: retrofit2.HttpException) {
                    if (ex.code() in 400..499 && ex.code() != 401 && ex.code() != 408 && ex.code() != 429) done += e
                } catch (_: Exception) {
                    reachable = false
                    break
                }
            }
            if (!reachable) break
        }

        // Drop exactly what was handled; anything recorded meanwhile stays.
        synchronized(kvLock) {
            writePending(readPending().filterNot { it in done })
        }
        if (legacy.isNotEmpty() && legacy.all { it in done }) store.kvSet(LEGACY_PENDING_KEY, "")
        reachable
    }

    /** Count the saved chapters the server has as read, and ask about them. */
    private suspend fun offerPurge(api: ApiService) {
        val saved = withContext(Dispatchers.IO) { ShioriRuntime.app.offline.listChapters() }
        if (saved.isEmpty()) return
        val keep = currentReadingKey
        val keys = ArrayList<String>()
        for ((seriesId, chapters) in saved.groupBy { it.seriesId }) {
            val server = runCatching { api.readerChapters(seriesId) }.getOrNull() ?: continue
            val read = server.chapters.filter { it.isCompleted }.map { chapterKey(seriesId, it.number) }.toSet()
            chapters.filter { it.chapterKey in read && it.chapterKey != keep }.forEach { keys += it.chapterKey }
        }
        if (keys.isEmpty()) return
        purgeKeys = keys
        _purgePrompt.value = keys.size
    }

    /** "Remove": delete the offered (already read) chapters, never the open one. */
    suspend fun confirmPurge(): Int {
        val keys = purgeKeys
        purgeKeys = emptyList()
        _purgePrompt.value = null
        val keep = currentReadingKey
        val removed = withContext(Dispatchers.IO) {
            keys.count { key ->
                if (key == keep) return@count false
                runCatching { ShioriRuntime.app.offline.deleteChapter(key) }.isSuccess
            }
        }
        if (removed > 0) _revision.update { it + 1 }
        return removed
    }

    /** "Keep": honoured until the next offline spell. */
    fun dismissPurge() {
        purgeKeys = emptyList()
        _purgePrompt.value = null
    }
}
