package app.renzoshiori.client.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** Mirrors ReaderBookmarkRequestDto (RenzoBackend Models/Dto/ReaderDtos.cs). */
@Serializable
data class ReaderBookmarkRequestDto(
    val seriesId: String = "",
    val chapterNumber: Double = 0.0,
    val bookmarked: Boolean = false,
)

/** POST /api/reader/clear-stream-cache → { success, cleared }. */
@Serializable
data class ClearStreamCacheResultDto(
    val success: Boolean = false,
    val cleared: Long = 0L,
)

/**
 * Reader endpoints that aren't on the shared [ApiService] — the bookmark toggle
 * and the streamed-page cache controls the reader's settings sheet exposes
 * (readerService.setBookmark / readerService.clearStreamCache on the web).
 * Obtained with `app.network.currentServiceOf<ReaderApi>()`.
 */
/**
 * POST /api/reader/trim-stream-cache → { success, cleared }. Keeps only these
 * chapters' streamed pages for the series (readerService.trimStreamCache on the
 * web). Numbers travel as JSON numbers and land server-side as decimals, so the
 * "60" / "60.0" spelling difference between surfaces does not matter.
 */
@Serializable
data class TrimStreamCacheRequestDto(
    val seriesId: String,
    val keep: List<Double>,
)

interface ReaderApi {
    @POST("api/reader/bookmark")
    suspend fun setBookmark(@Body body: ReaderBookmarkRequestDto)

    /** The controller takes no body; Retrofit sends a zero-length POST. */
    @POST("api/reader/clear-stream-cache")
    suspend fun clearStreamCache(): ClearStreamCacheResultDto

    @POST("api/reader/trim-stream-cache")
    suspend fun trimStreamCache(@Body body: TrimStreamCacheRequestDto): ClearStreamCacheResultDto
}
