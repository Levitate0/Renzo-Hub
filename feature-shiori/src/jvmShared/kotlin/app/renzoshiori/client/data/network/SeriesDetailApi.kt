package app.renzoshiori.client.data.network

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Everything the native series-detail screen needs that [ApiService] doesn't
 * already carry — the FULL SeriesExtendedDto (with its providers), the
 * per-source PATCH the web page's provider switches drive, the chapter
 * download/delete actions behind the chapters toolbar, plus the collections and
 * tracker endpoints behind the hero's action row.
 *
 * Kept in its own file so it can grow with the screen without touching the
 * shared [ApiService] other screens depend on. Obtain with
 * `app.network.currentServiceOf<SeriesDetailApi>()`.
 */
interface SeriesDetailApi {

    // ── Series (extended, with providers) ───────────────────────────────
    /** GET /api/serie — SeriesExtendedDto (BaseSeriesDto + providers + path + chapterList). */
    @GET("api/serie")
    suspend fun seriesExtended(@Query("id") id: String): SeriesExtendedDto

    /** PATCH /api/serie — the one write the whole sources card funnels through. */
    @PATCH("api/serie")
    suspend fun updateSeries(@Body body: SeriesExtendedDto): SeriesExtendedDto

    @DELETE("api/serie")
    suspend fun deleteSeries(
        @Query("id") id: String,
        @Query("alsoPhysical") alsoPhysical: Boolean,
    ): Response<ResponseBody>

    @GET("api/serie/verify")
    suspend fun verifyIntegrity(@Query("g") id: String): SeriesIntegrityResultDto

    @GET("api/serie/cleanup")
    suspend fun cleanupSeries(@Query("g") id: String): Response<ResponseBody>

    @POST("api/serie/refresh")
    suspend fun refreshSeries(
        @Query("id") id: String,
        @Query("ifStale") ifStale: Boolean = false,
    ): QueuedResultDto

    @POST("api/serie/scan")
    suspend fun scanSeries(@Query("id") id: String): QueuedResultDto

    @PUT("api/serie/{id}/category")
    suspend fun setCategory(
        @Path("id") id: String,
        @Body body: SetCategoryRequestDto,
    ): SetCategoryResultDto

    // ── Chapter actions ─────────────────────────────────────────────────
    @POST("api/serie/download-all")
    suspend fun downloadAll(@Query("seriesId") seriesId: String): QueuedResultDto

    /** Starts checking the series' locked chapters against the owner's site logins. */
    @POST("api/serie/check-purchases")
    suspend fun checkPurchases(@Query("seriesId") seriesId: String): PurchaseCheckDto

    /** Per-series auto-buy override (seriesService.ts setAutoBuy): true/false, or null to follow the account. */
    @POST("api/serie/auto-buy")
    suspend fun setAutoBuy(
        @Query("seriesId") seriesId: String,
        @Body body: AutoBuySettingDto,
    ): Response<ResponseBody>

    @GET("api/serie/check-purchases")
    suspend fun purchaseCheckStatus(@Query("seriesId") seriesId: String): PurchaseCheckDto

    @POST("api/serie/delete-downloads")
    suspend fun deleteDownloads(
        @Query("seriesId") seriesId: String,
        @Body body: DeleteDownloadsRequestDto,
    ): DeletedResultDto

    @POST("api/serie/chapter/redownload")
    suspend fun redownloadChapter(
        @Query("seriesId") seriesId: String,
        @Query("chapter") chapter: Double,
        @Query("providerId") providerId: String? = null,
    ): RedownloadResultDto

    // ── Latest downloads card ───────────────────────────────────────────
    @GET("api/downloads/series")
    suspend fun downloadsForSeries(@Query("seriesId") seriesId: String): List<app.renzoshiori.client.data.model.DownloadInfoDto>

    // ── Settings slice (reader on/off + category folders) ───────────────
    @GET("api/settings")
    suspend fun settings(): SeriesDetailSettingsDto

    /**
     * Saves a new order for the categorized-folder categories and nothing else
     * (owner only): every saved category, listed once. A different set is a 409
     * (the list changed elsewhere), and a server older than the endpoint 404s.
     */
    @PUT("api/settings/categories/order")
    suspend fun reorderCategories(@Body order: List<String>): Response<ResponseBody>

    // ── Collections (still "favorites" on the wire) ─────────────────────
    // The UI calls them collections; routes, DTOs and JSON fields kept the
    // old name so installed Hub builds keep talking to newer servers.
    @GET("api/favorites")
    suspend fun favorites(): List<FavoriteListDto>

    @POST("api/favorites")
    suspend fun createFavoriteList(@Body body: CreateFavoriteListDto): FavoriteListDto

    /** Deletes a collection, its sub-collections and their memberships (series stay in the library). */
    @DELETE("api/favorites/{id}")
    suspend fun deleteFavoriteList(@Path("id") listId: String): Response<ResponseBody>

    /**
     * Rewrites the order of ONE sibling group: every collection whose parent
     * is [ReorderFavoriteListsDto.parentId], listed exactly once. The server
     * answers 409 if that set is not exactly what it holds (a collection was
     * added or deleted elsewhere), so a stale order is refused, never half-written.
     * Response<ResponseBody>, not a decoded body: a server older than the
     * endpoint 404s, and the caller has to see that as a failure.
     */
    @PUT("api/favorites/order")
    suspend fun reorderFavoriteLists(@Body body: ReorderFavoriteListsDto): Response<ResponseBody>

    @POST("api/favorites/{id}/items")
    suspend fun addFavoriteItem(
        @Path("id") listId: String,
        @Body body: FavoriteItemRequestDto,
    ): Response<ResponseBody>

    @DELETE("api/favorites/{id}/items/{seriesId}")
    suspend fun removeFavoriteItem(
        @Path("id") listId: String,
        @Path("seriesId") seriesId: String,
    ): Response<ResponseBody>

    // ── Trackers (MAL / AniList / …) ────────────────────────────────────
    @GET("api/scrobbler/config")
    suspend fun scrobblerConfigs(): List<ScrobblerConfigDto>

    @GET("api/scrobbler/matches")
    suspend fun scrobblerMatches(): List<SeriesMatchStatusDto>

    @POST("api/scrobbler/matches/auto/{seriesId}")
    suspend fun autoMatchSeries(@Path("seriesId") seriesId: String): Response<ResponseBody>

    @POST("api/scrobbler/matches/disable")
    suspend fun disableTrackerLink(@Body body: DisableLinkRequestDto): Response<ResponseBody>
}

// ──────────────────────────────────────────────────────────────────────────
// DTOs — every field defaulted (kotlinx.serialization requirement) and named
// exactly as the backend's [JsonPropertyName] so the PATCH round-trips.
// ──────────────────────────────────────────────────────────────────────────

/** Mirrors ProviderExtendedDto — the per-source row of the Sources card. */
@Serializable
data class ProviderExtendedDto(
    val id: String = "",
    val provider: String = "",
    val scanlator: String = "",
    val lang: String = "",
    val title: String = "",
    val thumbnailUrl: String? = null,
    val url: String? = null,
    val status: Int = 0,
    val chapterCount: Long = 0,
    val chapterList: String = "",
    val lastChapter: Double? = null,
    val lastChangeUTC: String? = null,
    val lastUpdatedUTC: String? = null,
    val fromChapter: Double? = null,
    val priority: Int = 0,
    val isStorage: Boolean = false,
    val useCover: Boolean = false,
    val useTitle: Boolean = false,
    val useStatus: Boolean = false,
    val isUnknown: Boolean = false,
    val isLocal: Boolean = false,
    val isDisabled: Boolean = false,
    val isUninstalled: Boolean = false,
    val isDeleted: Boolean = false,
    val matchId: String? = null,
)

/** Mirrors SeriesExtendedDto (BaseSeriesDto + providers/chapterList/path). */
@Serializable
data class SeriesExtendedDto(
    val id: String = "",
    val title: String = "",
    val thumbnailUrl: String = "",
    val artist: String = "",
    val author: String = "",
    val description: String = "",
    val genre: List<String> = emptyList(),
    val status: Int = 0,
    val storagePath: String = "",
    val type: String? = null,
    val chapterCount: Int = 0,
    val lastChapter: Double? = null,
    val lastChangeUTC: String? = null,
    val isActive: Boolean = true,
    val pausedDownloads: Boolean = false,
    val hasUnknown: Boolean = false,
    val startFromChapter: Double? = null,
    val releaseCadenceDays: Int? = null,
    val category: String? = null,
    val nsfw: Boolean = false,
    val hideDecimalChapters: Boolean = false,
    val prioritizeFreeChapters: Boolean = false,
    /** Auto-buy override: true/false, or null to follow the account setting. Set via [SeriesDetailApi.setAutoBuy]. */
    val autoBuyPaidChapters: Boolean? = null,
    val isNsfw: Boolean = false,
    val chapterList: String = "",
    val path: String = "",
    val providers: List<ProviderExtendedDto> = emptyList(),
)

@Serializable
data class ArchiveIntegrityResultDto(
    /** ArchiveResult: 0 Fine, 1 NotAnArchive, 2 NoImages, 3 NotFound. */
    val result: Int = 0,
    val filename: String = "",
)

@Serializable
data class SeriesIntegrityResultDto(
    val success: Boolean = false,
    val badFiles: List<ArchiveIntegrityResultDto> = emptyList(),
)

@Serializable
data class AutoBuySettingDto(val value: Boolean? = null)

/** seriesService.ts PurchaseCheck — progress of a series' purchase check. */
@Serializable
data class PurchaseCheckDto(
    val running: Boolean = false,
    val total: Int = 0,
    val checked: Int = 0,
    val owned: Int = 0,
    val stillLocked: Int = 0,
    /** Couldn't tell: the source timed out or errored. */
    val failed: Int = 0,
    val error: String? = null,
)

@Serializable
data class QueuedResultDto(
    val success: Boolean = false,
    val queued: Int = 0,
    val pruned: Int = 0,
)

@Serializable
data class DeletedResultDto(
    val success: Boolean = false,
    val deleted: Int = 0,
)

@Serializable
data class RedownloadResultDto(
    val success: Boolean = false,
    val queued: Int = 0,
    val sourceProviderName: String? = null,
    val error: String? = null,
)

@Serializable
data class DeleteDownloadsRequestDto(
    val chapterNumbers: List<Double>? = null,
)

@Serializable
data class SetCategoryRequestDto(
    val category: String? = null,
)

@Serializable
data class SetCategoryResultDto(
    val success: Boolean = false,
    val moved: Boolean = false,
    val storagePath: String = "",
    val detail: String? = null,
)

/** Only the settings slice the series page reads. */
@Serializable
data class SeriesDetailSettingsDto(
    val categorizedFolders: Boolean = true,
    val categories: List<String> = emptyList(),
)

@Serializable
data class FavoriteListDto(
    val id: String = "",
    val name: String = "",
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val seriesIds: List<String> = emptyList(),
)

@Serializable
data class CreateFavoriteListDto(
    val name: String = "",
    val parentId: String? = null,
)

@Serializable
data class FavoriteItemRequestDto(
    val seriesId: String = "",
)

/**
 * PUT /api/favorites/order. A null [parentId] (the top-level collections)
 * leaves the body entirely — this module's Json has encodeDefaults = false and
 * explicitNulls = false — and the server reads an absent parentId as null,
 * so the two mean the same thing.
 */
@Serializable
data class ReorderFavoriteListsDto(
    val parentId: String? = null,
    val ids: List<String> = emptyList(),
)

/** ScrobblerProvider (int): 0 MyAnimeList, 1 AniList, 2 ComicVine, 3 Kitsu, 4 MangaDex. */
@Serializable
data class ScrobblerConfigDto(
    val provider: Int = 0,
    val displayName: String = "",
    val isEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val autoSync: Boolean = false,
)

/** SeriesMappingStatus (int): 0 Unmatched, 1 AutoMatched, 2 UserConfirmed, 3 Ignored. */
@Serializable
data class SeriesMatchStatusDto(
    val seriesId: String = "",
    val seriesTitle: String = "",
    val provider: Int = 0,
    val mappingStatus: Int = 0,
    val externalSeriesId: String? = null,
    val externalSeriesTitle: String? = null,
    val externalSeriesUrl: String? = null,
)

@Serializable
data class DisableLinkRequestDto(
    val seriesId: String = "",
    val provider: Int = 0,
)
