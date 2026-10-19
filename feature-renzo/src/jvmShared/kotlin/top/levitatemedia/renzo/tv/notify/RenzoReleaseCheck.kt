package top.levitatemedia.renzo.tv.notify

import kotlinx.serialization.Serializable
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.notify.ReleaseAlerts
import top.levitatemedia.renzo.hub.core.notify.ReleaseNotice
import top.levitatemedia.renzo.tv.Prefs
import top.levitatemedia.renzo.tv.api.ApiClient
import top.levitatemedia.renzo.tv.api.ContentLevel

/** GET /api/library/release-heads (renzo src/routes/api.ts). */
@Serializable
data class ReleaseHeadsResponse(
    val now: String = "",
    val titles: List<ReleaseHeadTitle> = emptyList(),
)

@Serializable
data class ReleaseHeadTitle(
    val id: Int,
    val type: String = "series",
    val title: String = "",
    val poster: String? = null,
    /** Episodes aired, the count the Updates feed uses; 1 for a released movie. */
    val aired: Int = 0,
    val status: String? = null,
    val content: List<String> = emptyList(),
)

/**
 * The anime half of the background new-release check. See [ReleaseAlerts]
 * for what counts as new: here the mark is the aired count, so a better
 * release of an episode, or a re-download, is never news.
 */
object RenzoReleaseCheck {
    /** The notices to post. Throws when the server can't be reached; the caller tries again later. */
    suspend fun run(prefs: Prefs, client: ApiClient): List<ReleaseNotice> {
        val target = HubTarget.Renzo
        if (!ReleaseAlerts.anyOn(target)) return emptyList()
        if (prefs.serverUrl == null || prefs.sessionCookie == null) return emptyList()

        val primed = ReleaseAlerts.primed(target)
        val heads: ReleaseHeadsResponse = client.get("/api/library/release-heads")
        val level = prefs.contentLevel
        val notices = mutableListOf<ReleaseNotice>()
        for (t in heads.titles) {
            val id = t.id.toString()
            val aired = t.aired.toDouble()
            val mark = ReleaseAlerts.mark(target, id)
            if (!primed || mark == null) {
                ReleaseAlerts.raiseMark(target, id, aired)
                continue
            }
            if (aired <= mark) continue
            if (ReleaseAlerts.isOn(target, id) && ContentLevel.allows(level, t.content)) {
                notices += ReleaseNotice(
                    target = target,
                    id = id,
                    title = t.title,
                    coverUrl = t.poster,
                    numbers = (mark.toInt() + 1..t.aired).map { it.toDouble() },
                    movie = t.type == "movie",
                )
            }
            ReleaseAlerts.raiseMark(target, id, aired)
        }
        ReleaseAlerts.setSince(target, heads.now)
        ReleaseAlerts.setPrimed(target, true)
        return notices
    }
}
