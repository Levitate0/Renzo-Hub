package app.renzoshiori.client.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/** Mirrors RenzoBackend/Models/Enums/SeriesStatus.cs (serialized as int). */
object SeriesStatus {
    const val UNKNOWN = 0
    const val ONGOING = 1
    const val COMPLETED = 2
    const val LICENSED = 3
    const val PUBLISHING_FINISHED = 4
    const val CANCELLED = 5
    const val ON_HIATUS = 6

    fun label(v: Int): String = when (v) {
        ONGOING -> "Ongoing"
        COMPLETED -> "Completed"
        LICENSED -> "Licensed"
        PUBLISHING_FINISHED -> "Finished"
        CANCELLED -> "Cancelled"
        ON_HIATUS -> "On hiatus"
        else -> "Unknown"
    }
}

@Serializable
data class SmallProviderDto(
    val id: String? = null,
    val provider: String = "",
    val language: String? = null,
)

/** Mirrors SeriesInfoDto/BaseSeriesDto — only fields the native client reads. */
@Serializable
data class SeriesInfoDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String = "",
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: Int = SeriesStatus.UNKNOWN,
    val chapterCount: Int = 0,
    val lastChapter: Double? = null,
    val lastChangeUTC: String? = null,
    val isActive: Boolean = true,
    val pausedDownloads: Boolean = false,
    val hasUnknown: Boolean = false,
    val category: String? = null,
    val isNsfw: Boolean = false,
    val lastChangeProvider: SmallProviderDto? = null,
)

@Serializable
data class ChapterSourceDto(
    val id: String,
    val name: String,
)

/** Mirrors ChapterDetailDto — the merged per-series chapter list. */
@Serializable
data class ChapterDetailDto(
    val number: Double? = null,
    val name: String = "",
    val downloaded: Boolean = false,
    val sourceProviderId: String? = null,
    val sourceProviderName: String? = null,
    val uploadDate: String? = null,
    val url: String? = null,
    val locked: Boolean = false,
    val availableProviders: List<ChapterSourceDto> = emptyList(),
    /**
     * Chapter numbers absent between THIS chapter and the next higher listed
     * one, grouped into ascending runs; null/absent when there is no gap.
     * The series chapter list draws one "Missing · Ch. …" gap row per run.
     * Twin: ChapterDetail.missingAfter in shiori/RenzoFrontend/src/lib/api/types.ts
     * (drawn by chapters-section.tsx / chapter-row.tsx).
     */
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("MissingAfter") // the server sets no naming policy; unattributed = PascalCase
    val missingAfter: List<MissingRangeDto>? = null,
)

/**
 * One run of absent chapter numbers inside [ChapterDetailDto.missingAfter].
 * Twin: ChapterGap in shiori/RenzoFrontend/src/lib/api/types.ts.
 * Double, never Float: a 32-bit float turns 28.1 into 28.100000381….
 * The PascalCase alternates are a hedge, not the contract (the contract is
 * camelCase): an unattributed C# property would otherwise fail this whole
 * chapter-list parse, and the list would silently fall back to reader rows.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MissingRangeDto(
    @JsonNames("From") val from: Double,
    @JsonNames("To") val to: Double,
    @JsonNames("Count") val count: Int,
)

// ── Reader DTOs (Models/Dto/ReaderDtos.cs) ─────────────────────────────────

@Serializable
data class ReaderChapterDto(
    val number: Double,
    val name: String = "",
    val filename: String? = null,
    val pageCount: Int? = null,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val bookmarked: Boolean = false,
    val lastReadAt: String? = null,
    val locked: Boolean = false,
    val url: String? = null,
)

@Serializable
data class ReaderChaptersDto(
    val seriesId: String,
    val title: String = "",
    val type: String? = null,
    val chapters: List<ReaderChapterDto> = emptyList(),
)

@Serializable
data class ReaderPageDimsDto(
    val index: Int,
    val width: Int? = null,
    val height: Int? = null,
    val isStrip: Boolean = false,
    val isSliver: Boolean = false,
)

@Serializable
data class ReaderChapterInfoDto(
    val filename: String = "",
    val pageCount: Int = 0,
    val suggestedMode: String = "paged",
    val pages: List<ReaderPageDimsDto> = emptyList(),
)

@Serializable
data class PreviewPagesDto(
    val pageCount: Int = 0,
    val locked: Boolean = false,
    /** The source that served a streamed library chapter ("Switch source"). */
    val source: String? = null,
    /** How many of the series' sources list it; "Switch source" shows when more than one. */
    val sourceCount: Int = 0,
)

/** One chapter of a Browse item, straight from the source (nothing stored).
 *  `index` is the SOURCE list position — the page endpoints key on it. */
@Serializable
data class PreviewChapterDto(
    val index: Int = 0,
    val name: String = "",
    val number: Double? = null,
    val dateUpload: String? = null,
)

@Serializable
data class PreviewChaptersDto(
    val mihonId: String = "",
    val title: String = "",
    val chapters: List<PreviewChapterDto> = emptyList(),
)

@Serializable
data class ReaderProgressRequestDto(
    val seriesId: String,
    val chapterNumber: Double,
    val lastReadPage: Int,
    val totalPages: Int,
    val filename: String? = null,
)

@Serializable
data class ReaderMarkRequestDto(
    val seriesId: String,
    val chapterNumbers: List<Double>,
    val read: Boolean,
)

/**
 * readerService.ts AutoBuyResult — answer of /api/reader/auto-buy. status is
 * "running" | "bought" | "failed" | "off"; on "failed", [message] says which
 * requirement wasn't met, for the purchase screen.
 */
@Serializable
data class AutoBuyResultDto(
    val status: String = "off",
    val reason: String? = null,
    val message: String? = null,
    val provider: String? = null,
    val price: Double? = null,
    val balance: Double? = null,
)
