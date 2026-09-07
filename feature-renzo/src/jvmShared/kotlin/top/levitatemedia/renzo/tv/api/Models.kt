package top.levitatemedia.renzo.tv.api

import kotlinx.serialization.Serializable

// DTOs mirroring the server's JSON (ground truth: fullstack-arr
// frontend/src/lib/types.ts + src/routes/api.ts). Parsed with
// ignoreUnknownKeys so server additions never break the TV client.

@Serializable
data class AddDefaults(
    val track: String? = null,
    val autoDownload: Boolean? = null,
    val folder: String? = null,
)

@Serializable
data class PublicUser(
    val id: String,
    val username: String,
    val role: String = "user", // owner | manager | user
    val email: String? = null,
    val realDebridConnected: Boolean = false,
    val allDebridConnected: Boolean = false,
    val debrid: String? = null,
    val jimakuConnected: Boolean = false,
    val anilistConnected: Boolean = false,
    val malConnected: Boolean = false,
    val downloadsDenied: Boolean = false,
    val autoStatus: Boolean = false,
    val ccLang: String = "en",
    val addDefaults: AddDefaults? = null,
    val avatarBase64: String? = null,
    val avatarContentType: String? = null,
    /** Only present on the /account/realdebrid|alldebrid save responses. */
    val premium: Boolean? = null,
)

@Serializable
data class MeResponse(
    val user: PublicUser? = null,
    val setupRequired: Boolean? = null,
    val authDisabled: Boolean? = null,
)

@Serializable
data class LoginResponse(val user: PublicUser)

/**
 * `/api/account/oauth/:provider/poll`, which answers 200 with TWO shapes:
 * `{pending:true}` while the browser half of the flow is unfinished, and
 * `{connected:true, user:{...}}` once it lands.
 *
 * Every field is optional for that reason. This was decoded as [LoginResponse]
 * — whose `user` is required — so the FIRST poll, the one that is supposed to
 * say "not yet", threw "Field 'user' is required ... missing at path: $" and
 * surfaced as an error dialog over the Account screen instead of a wait.
 */
@Serializable
data class OAuthPollResponse(
    val pending: Boolean = false,
    val connected: Boolean = false,
    val user: PublicUser? = null,
)

/** One poster card — discover rows, library, updates, history all reuse it. */
@Serializable
data class CardItem(
    val id: Int? = null,           // AniList id; null for MAL fallback cards
    val malId: Int? = null,
    val source: String? = null,    // "mal" on AniList-down fallback cards
    val type: String = "series",   // series | movie
    val title: String = "",
    val year: Int? = null,
    val poster: String? = null,
    val genres: List<String> = emptyList(),
    val content: List<String> = emptyList(), // hentai | ecchi | erotica
    // per-user extras (library/updates/history)
    val inLibrary: Boolean? = null,
    val lists: List<String> = emptyList(),
    val folder: String? = null,
    val downloaded: Int? = null,
    val upNext: Int? = null,
    val seasonCount: Int? = null,
    // updates-feed extras
    val updKind: String? = null,   // episode | movie | season (client-side name)
    val kind: String? = null,      // the server's field name on /api/updates items
    val ep: Int? = null,
    val season: Int? = null,
    val upcoming: Boolean? = null, // season news: true => "Soon" ribbon
    // history extras
    val at: Long? = null,
)

@Serializable
data class ResolveResponse(val id: Int)

@Serializable
data class SeasonRef(
    val id: Int,
    val num: Int? = null,
    val part: Int? = null,
    val kind: String? = null,     // season | movie | extra
    val format: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val year: Int? = null,
)

@Serializable
data class EpisodeInfo(
    val number: Int,
    val status: String? = null,    // aired/soon etc.
    val hasFile: Boolean = false,
    val progress: Double? = null,  // 0..1 fraction while downloading, not a %
    val aired: String? = null,
    val thumbnail: String? = null,
    val epTitle: String? = null,
)

@Serializable
data class TitleDetail(
    val id: Int,
    val malId: Int? = null,
    val type: String = "series",
    val format: String? = null,
    val romaji: String = "",
    val english: String? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val availableEpisodes: Int? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val content: List<String> = emptyList(),
    val isAdult: Boolean = false,
    val poster: String? = null,
    val banner: String? = null,
    val airingStatus: String? = null,
    val nextAiringEpisode: Int? = null,
    val duration: Int? = null,
    val seasonNum: Int? = null,
    val seasonPart: Int? = null,
    val seasonKind: String? = null,
    val seasons: List<SeasonRef> = emptyList(),
    val nextUp: SeasonRef? = null,
    val episodeList: List<EpisodeInfo> = emptyList(),
    val watchedThrough: Int = 0,
    val inLibrary: Boolean = false,
    val lists: List<String> = emptyList(),
    val folder: String? = null,
    /** Hero detail-controls (web DetailModel): auto-download flag, the user's
     *  folder list for the picker, and the pinned release group. */
    val autoDownload: Boolean = false,
    val folders: List<String> = emptyList(),
    val provider: String? = null,
) {
    val displayTitle: String get() = english?.takeIf { it.isNotBlank() } ?: romaji
}

@Serializable
data class SubtitleRef(
    val id: String,
    val label: String = "",
    val lang: String = "",
)

@Serializable
data class DownloadJob(
    val id: String = "",
    val status: String = "",
    /**
     * 0..1 fraction, NOT a percentage. Typed Int here originally, which threw
     * on decode and took the entire /play response with it — so playback broke
     * exactly when a background download for that episode was running.
     */
    val progress: Double? = null,
    val message: String? = null,
)

/** GET /api/titles/{id}/resume — keyed by episode number as a string. */
@Serializable
data class ResumePoint(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
)

/** One entry of POST /api/titles/{id}/play. */
@Serializable
data class BatchPlayResult(
    val ep: Int = 0,
    val ok: Boolean = false,
    val stream: ResolvedStream? = null,
    val error: String? = null,
    /** "ask again shortly" — the resolve is still running server-side. */
    val retriable: Boolean = false,
)

@Serializable
data class BatchPlayResponse(val results: List<BatchPlayResult> = emptyList())

/**
 * GET /api/titles/{id}/offline/{ep} — local file + caption URLs pre-signed with
 * short-lived download tokens, built for exactly this downloader. 409s when the
 * episode is debrid-only rather than in the server's library.
 */
@Serializable
data class OfflineLinks(
    val source: String = "local",
    val url: String,
    val subtitles: List<OfflineSubtitle> = emptyList(),
)

@Serializable
data class OfflineSubtitle(
    val label: String = "",
    val lang: String = "",
    val src: String,
)

@Serializable
data class ResolvedStream(
    val source: String,            // local | realdebrid | alldebrid
    val url: String,               // absolute https (debrid) or relative /files/... (local)
    val filename: String? = null,
    val subtitles: List<SubtitleRef> = emptyList(),
    val downloading: DownloadJob? = null,
)

/** One row from GET /api/jobs (internal fields already stripped server-side). */
@Serializable
data class JobItem(
    val id: String,
    val titleId: Int = 0,
    val episode: Int = 0,
    val status: String = "",   // queued | searching | downloading | downloaded | failed
    val progress: Double = 0.0, // 0..1 fraction (web: Math.round(progress * 100)%)
    val message: String? = null,
    val title: String = "",
    val mine: Boolean = true,  // false = another user's job (admin sees all)
) {
    val active: Boolean get() = status == "queued" || status == "searching" || status == "downloading"
}

@Serializable
data class WatchedResponse(val ok: Boolean = true, val upNext: Int? = null)

@Serializable
data class ProgressResponse(val id: Int? = null, val watchedThrough: Int = 0, val upNext: Int? = null)

@Serializable
data class FolderInfo(val name: String, val count: Int = 0, val default: Boolean = false)

/** POST /api/trackers/import result (library Import AniList / MAL button). */
@Serializable
data class ImportResult(val anilist: Int = 0, val mal: Int = 0)

/** Auto-downloader self-check finding (server selfcheck.PublicCheck). */
@Serializable
data class AutodlCheck(
    val code: String = "",
    val scope: String = "server", // server | you | user
    val user: String? = null,
    val severity: String = "warn",
    val since: String? = null,
    val message: String = "",
    /** e.g. "settings:credentials" — clicking should open that settings pane. */
    val action: String? = null,
)

/** GET /api/autodl/status. */
@Serializable
data class AutodlStatus(
    val enabled: Boolean = false,
    val intervalMin: Int = 0,
    val maxPerTick: Int = 0,
    val running: Boolean = false,
    val lastRun: String? = null,
    val lastQueued: Int = 0,
    val lastError: String? = null,
    val trackedTitles: Int = 0,
    val scope: String = "server", // server | you
    val canRun: Boolean = false,
    val checks: List<AutodlCheck> = emptyList(),
)

/** POST /api/autodl/run. */
@Serializable
data class AutodlRunResult(val queued: Int = 0)

@Serializable
data class VersionResponse(val build: String)

@Serializable
data class TrackerFlags(val anilist: Boolean = false, val mal: Boolean = false)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val realdebrid: String = "not-connected",
    val alldebrid: String = "not-connected",
    val debrid: String? = null,
    val trackers: TrackerFlags = TrackerFlags(),
)

@Serializable
data class ApiKeyInfo(
    val apiKey: String,
    val renzoUrl: String? = null,
    /** GitHub-hosted Jellyfin plugin repository URL (web apikey pane). */
    val manifestUrl: String? = null,
)

@Serializable
data class GravatarResult(val avatarBase64: String, val avatarContentType: String)

@Serializable
data class AvatarSetResult(
    val ok: Boolean = true,
    val avatarBase64: String? = null,
    val avatarContentType: String? = null,
)

@Serializable
data class OAuthStart(val authUrl: String, val state: String)

@Serializable
data class ListToggleResult(
    val id: Int? = null,
    val lists: List<String> = emptyList(),
    val inLibrary: Boolean = false,
)

@Serializable
data class SeasonDownloadResult(val queued: Int = 0)

/** GET /api/invites — pending, unexpired invite links. */
@Serializable
data class InviteItem(
    val token: String,
    val role: String = "user",
    val email: String? = null,
    val username: String? = null,
    val expiresAt: String = "",
    val url: String = "",
)

/** GET/POST /api/smtp — password is never returned (hasPassword instead). */
@Serializable
data class SmtpSettings(
    val host: String = "",
    val port: Int = 587,
    val secure: Boolean = false,
    val user: String = "",
    val from: String = "",
    val hasPassword: Boolean = false,
)

/** GET /api/titles/:id/providers — release groups seen for this title. */
@Serializable
data class ProviderOption(
    val group: String,
    val count: Int = 0,
    val resolutions: List<Int> = emptyList(),
)

@Serializable
data class AutoResult(val autoDownload: Boolean = false)

@Serializable
data class FolderResult(val id: Int? = null, val folder: String = "")

@Serializable
data class ProviderResult(val id: Int? = null, val provider: String? = null)

/**
 * One tracker's list entry (`tracker.ts:271 TrackEntry`, mirrored in the web's
 * `lib/types.ts:325`).
 */
@Serializable
data class TrackEntry(
    val status: String? = null,
    val progress: Int = 0,
    val score: Double = 0.0,
    val total: Int? = null,
)

/**
 * GET/POST /api/titles/:id/tracking — AniList/MAL status for this title.
 *
 * The server's shape is NESTED and per-tracker (`tracker.ts:272`):
 * `{ anilist?: TrackEntry|null, mal?: TrackEntry|null }`. Key PRESENCE is the
 * connected test, and a present-but-null value means "connected but the lookup
 * failed" — which is why the two `*Connected` flags are carried separately
 * instead of being derived from a null check. kotlinx.serialization cannot tell
 * key-absent from key-present-null, so [Repo.tracking] parses this by hand and
 * this class is deliberately NOT `@Serializable`.
 *
 * This previously declared a FLAT `(status, progress, score, connected)`. The
 * server has never emitted any of those keys at that level, so with
 * `ignoreUnknownKeys = true` every response decoded to all-defaults and
 * `connected` was permanently false — the tracking row told every user to
 * "Connect AniList or MAL in Settings" even with both linked.
 */
data class Tracking(
    val anilist: TrackEntry? = null,
    val mal: TrackEntry? = null,
    val anilistConnected: Boolean = false,
    val malConnected: Boolean = false,
) {
    val connected: Boolean get() = anilistConnected || malConnected

    /** Display prefers AniList (web `tracking-row.tsx:46`). */
    val entry: TrackEntry? get() = anilist ?: mal

    /** Web `tracking-row.tsx:38-41`. */
    val providers: List<String> get() = buildList {
        if (anilistConnected) add("AniList")
        if (malConnected) add("MyAnimeList")
    }
}

/** Cumulative adult-content ladder, mirroring the web client. */
object ContentLevel {
    private val rank = mapOf("none" to 0, "ecchi" to 1, "erotica" to 2, "hentai" to 3)
    fun allows(level: String, tags: List<String>): Boolean {
        if (tags.isEmpty()) return true
        val max = tags.maxOf { rank[it] ?: 0 }
        return max <= (rank[level] ?: 1)
    }
}
