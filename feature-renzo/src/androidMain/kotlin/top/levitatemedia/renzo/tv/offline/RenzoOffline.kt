package top.levitatemedia.renzo.tv.offline

import android.content.Context
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.offline.AssetRole
import top.levitatemedia.renzo.hub.core.offline.DownloadJob
import top.levitatemedia.renzo.hub.core.offline.DownloadQueue
import top.levitatemedia.renzo.hub.core.offline.DownloadSource
import top.levitatemedia.renzo.hub.core.offline.DownloadSources
import top.levitatemedia.renzo.hub.core.offline.HubDownloadService
import top.levitatemedia.renzo.hub.core.offline.OfflineLibrary
import top.levitatemedia.renzo.hub.core.offline.OfflineStore
import top.levitatemedia.renzo.hub.core.offline.PendingAsset
import top.levitatemedia.renzo.hub.core.offline.PendingItem
import top.levitatemedia.renzo.tv.Prefs
import top.levitatemedia.renzo.tv.api.ApiClient
import top.levitatemedia.renzo.tv.api.Repo
import top.levitatemedia.renzo.tv.api.TitleDetail

/** "{titleId}:{ep}" — unique within the Renzo half. */
fun episodeKey(titleId: Int, ep: Int): String = "$titleId:$ep"

private fun parseKey(itemKey: String): Pair<Int, Int>? {
    val parts = itemKey.split(":")
    if (parts.size != 2) return null
    val id = parts[0].toIntOrNull() ?: return null
    val ep = parts[1].toIntOrNull() ?: return null
    return id to ep
}

/**
 * Resolves an episode's files at download time, not at enqueue time.
 *
 * This is the whole reason [DownloadSource] exists. `GET /api/titles/{id}/play/{ep}`
 * hands back a debrid link that is signed and short-lived; a batch queued at
 * 09:00 and reached at 11:00 would carry dead URLs for everything after the
 * first item. The manga half has no such problem — its page routes are stable,
 * so it bakes them in.
 */
class RenzoDownloadSource(private val repo: Repo) : DownloadSource {

    /**
     * Warm the server's stream cache for the whole job in one call.
     *
     * Without this a season download pays a serial ~45s resolve per episode
     * before any bytes move. The batch endpoint resolves at concurrency 3 and
     * answers within ~55s; anything that misses that deadline keeps resolving
     * server-side into the 8-minute cache, so the per-item resolve that follows
     * still benefits. Purely an optimisation — [resolveAssets] is authoritative,
     * and a 404 here just means an older server.
     */
    override suspend fun prepare(job: DownloadJob) {
        val titleId = job.parentId.toIntOrNull() ?: return
        val episodes = job.items.mapNotNull { parseKey(it.itemKey)?.second }
        if (episodes.isEmpty()) return
        // The server rejects the excess beyond 24 as retriable rather than
        // erroring, but chunking keeps each call inside the read timeout.
        episodes.chunked(24).forEach { chunk ->
            runCatching { repo.playBatch(titleId, chunk) }
        }
    }

    override suspend fun resolveAssets(job: DownloadJob, item: PendingItem): List<PendingAsset> {
        val (titleId, ep) = parseKey(item.itemKey) ?: return emptyList()

        // Preferred: the server's purpose-built offline endpoint. It returns the
        // local file plus caption URLs already signed with per-path download
        // tokens, so the captions work without relying on the session cookie —
        // /api/captions sits behind the CSRF guard, /dl/captions does not.
        // It 409s when the episode is debrid-only rather than in the library.
        runCatching { repo.offlineLinks(titleId, ep) }.getOrNull()?.let { links ->
            if (links.url.isNotBlank() && !isPlaylist(links.url)) {
                return buildList {
                    add(PendingAsset(role = AssetRole.VIDEO, index = 0, url = links.url))
                    links.subtitles.forEachIndexed { i, sub ->
                        add(
                            PendingAsset(
                                role = AssetRole.SUBTITLE,
                                index = i,
                                url = sub.src,
                                label = sub.label.ifBlank { sub.lang },
                            ),
                        )
                    }
                }
            }
        }

        // Debrid-only episode: resolve a fresh link. This is the call that must
        // happen HERE and not at enqueue time — the link is short-lived and
        // IP-bound.
        val stream = runCatching { repo.play(titleId, ep) }.getOrNull() ?: return emptyList()
        if (stream.url.isBlank()) return emptyList()
        // A provider that unrestricted a playlist instead of a media file would
        // otherwise be saved as a few KB of text and played as a broken episode.
        if (isPlaylist(stream.url)) return emptyList()

        return buildList {
            add(PendingAsset(role = AssetRole.VIDEO, index = 0, url = stream.url))
            // Sidecar subtitles are tiny and fetched in parallel with nothing;
            // they carry the language so the player can label the track.
            stream.subtitles.forEachIndexed { i, sub ->
                add(
                    PendingAsset(
                        role = AssetRole.SUBTITLE,
                        index = i,
                        url = "/api/captions/${sub.id}.vtt",
                        label = sub.label.ifBlank { sub.lang },
                    ),
                )
            }
        }
    }

    /** Path ends in a streaming manifest — not something to save as a file. */
    private fun isPlaylist(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mpd")
    }
}

/**
 * Wiring the Renzo half contributes to the Hub's shared downloader.
 *
 * Called from the Application, NOT from a composable. A queued download has to
 * keep running while the manga half is on screen, or after the process is
 * restarted with no UI at all — if the signer were registered by RenzoRoot,
 * a restart with a pending queue would fetch unauthenticated and 401.
 */
object RenzoOfflineWiring {
    fun install(context: Context) {
        val prefs = Prefs(context.applicationContext)

        HubSession.register(HubTarget.Renzo) { builder ->
            prefs.sessionCookie?.let { builder.header("Cookie", "fsa_session=$it") }
        }
        DownloadSources.register(HubTarget.Renzo, RenzoDownloadSource(Repo(ApiClient(prefs))))
    }
}

/** Read side for the anime half. */
fun renzoOfflineLibrary(store: OfflineStore) = OfflineLibrary(store, HubTarget.Renzo)

/**
 * Queue episodes of one title for download.
 *
 * Note what is NOT here: any playback URL. Only the title and episode numbers
 * are stored, and [RenzoDownloadSource] resolves the rest when its turn comes.
 */
fun enqueueEpisodes(
    context: Context,
    detail: TitleDetail,
    episodes: List<Int>,
    baseUrl: String,
) {
    if (episodes.isEmpty()) return
    val items = episodes.map { ep ->
        PendingItem(
            itemKey = episodeKey(detail.id, ep),
            ordinal = ep.toDouble(),
            title = "${detail.displayTitle} · Episode $ep",
            assets = null, // resolved late — see RenzoDownloadSource
        )
    }
    DownloadQueue(context.applicationContext).enqueue(
        DownloadJob(
            target = HubTarget.Renzo,
            baseUrl = baseUrl,
            parentId = detail.id.toString(),
            parentTitle = detail.displayTitle,
            parentDescription = detail.description,
            coverUrl = detail.poster,
            items = items,
        ),
    )
    HubDownloadService.start(context.applicationContext)
}
