package app.renzoshiori.client.data.notify

import app.renzoshiori.client.ShioriApp
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.ui.util.AdultFilter
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.notify.IsoTime
import top.levitatemedia.renzo.hub.core.notify.ReleaseAlerts
import top.levitatemedia.renzo.hub.core.notify.ReleaseNotice

/** GET /api/serie/release-heads (RenzoBackend ReleaseHeadsDto). */
@Serializable
data class ReleaseHeadsDto(
    val now: String = "",
    val series: List<ReleaseHeadDto> = emptyList(),
    val fresh: List<FreshChapterDto> = emptyList(),
)

@Serializable
data class ReleaseHeadDto(val id: String, val last: Double? = null)

@Serializable
data class FreshChapterDto(
    val seriesId: String,
    val seriesTitle: String = "",
    val thumbnailUrl: String? = null,
    val isNsfw: Boolean = false,
    val chapterNumber: Double,
    val chapterName: String? = null,
    val found: String? = null,
    val published: String? = null,
)

interface ReleasesApi {
    @GET("api/serie/release-heads")
    suspend fun heads(@Query("since") since: String?): ReleaseHeadsDto
}

/**
 * The manga half of the background new-release check. See [ReleaseAlerts]
 * for what counts as new.
 */
object ShioriReleaseCheck {
    /**
     * A chapter must have come out within this long to be announced. A source
     * added late can bring chapters that are new to the library but went up
     * weeks ago. They are above the mark and still not new releases, so their
     * own publish date rules them out. Generous enough for a server that only
     * looks for chapters every few days.
     */
    private const val RECENT_MS = 14L * 24 * 3600_000

    /**
     * The window asked for overlaps the last one by this much. A chapter found
     * while the previous answer was being built would otherwise fall between
     * the two. Anything seen twice is below the mark the second time.
     */
    private const val OVERLAP_MS = 10L * 60_000

    /** The notices to post. Throws when the server can't be reached; the caller tries again later. */
    suspend fun run(app: ShioriApp, nowMs: Long = System.currentTimeMillis()): List<ReleaseNotice> {
        val target = HubTarget.Shiori
        if (!ReleaseAlerts.anyOn(target)) return emptyList()
        val store = app.tokenStore
        val server = store.serverUrl ?: return emptyList()
        // Signed out: nothing to ask with. The baseline waits for a sign-in.
        if (store.accessToken == null && store.refreshCookie == null) return emptyList()
        val api = app.network.currentServiceOf<ReleasesApi>() ?: return emptyList()

        val primed = ReleaseAlerts.primed(target)
        val since = if (primed) IsoTime.parse(ReleaseAlerts.since(target))?.let { IsoTime.format(it - OVERLAP_MS) } else null
        val heads = api.heads(since)

        val freshBySeries = heads.fresh.groupBy { it.seriesId }
        val hideAdult = AdultFilter.isHidden()
        val notices = mutableListOf<ReleaseNotice>()
        for (head in heads.series) {
            val last = head.last ?: continue
            val mark = ReleaseAlerts.mark(target, head.id)
            if (!primed || mark == null) {
                // Baseline, or a series added since the last check: where it
                // stands now is not news.
                ReleaseAlerts.raiseMark(target, head.id, last)
                continue
            }
            val news = freshBySeries[head.id].orEmpty()
                .filter { it.chapterNumber > mark && recent(it, nowMs) }
                .distinctBy { it.chapterNumber }
                .sortedBy { it.chapterNumber }
            val first = news.firstOrNull()
            if (first != null && ReleaseAlerts.isOn(target, head.id) && !(hideAdult && first.isNsfw)) {
                notices += ReleaseNotice(
                    target = target,
                    id = head.id,
                    title = first.seriesTitle,
                    coverUrl = first.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { absoluteUrl(server, it) },
                    numbers = news.map { it.chapterNumber },
                    name = news.singleOrNull()?.chapterName,
                )
            }
            ReleaseAlerts.raiseMark(target, head.id, maxOf(last, news.lastOrNull()?.chapterNumber ?: last))
        }
        ReleaseAlerts.setSince(target, heads.now)
        ReleaseAlerts.setPrimed(target, true)
        return notices
    }

    private fun recent(c: FreshChapterDto, nowMs: Long): Boolean {
        val out = IsoTime.parse(c.published) ?: return true // no date: found since the last check is all we know
        return nowMs - out <= RECENT_MS
    }
}
