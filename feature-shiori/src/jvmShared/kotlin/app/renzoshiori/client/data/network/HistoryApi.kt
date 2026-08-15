package app.renzoshiori.client.data.network

import app.renzoshiori.client.data.model.HistoryFeedItemDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Reading-history feed (HANDOFF_renzohub_historytab.md §1). The route is
 * `api/serie`, SINGULAR — SeriesController declares [Route("api/serie")], and
 * "correcting" it to /api/series yields a 404.
 */
interface HistoryApi {
    @GET("api/serie/history")
    suspend fun history(
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 500,
        /** Owner-level only: every user's history. Ignored for anyone else. */
        @Query("viewAll") viewAll: Boolean = false,
    ): List<HistoryFeedItemDto>
}
