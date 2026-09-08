package app.renzoshiori.client.data.network

import app.renzoshiori.client.data.model.ContentPreferencesDto
import app.renzoshiori.client.data.model.ServerSettingsDto
import app.renzoshiori.client.data.model.SettingsUpdateResponseDto
import app.renzoshiori.client.data.model.TestEmailRequestDto
import app.renzoshiori.client.data.model.TestEmailResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * Instance-wide settings. GET is open to any authenticated user; PUT is
 * Owner-only server-side, and the full DTO must be round-tripped (see the
 * nullability note on [ServerSettingsDto]).
 */
interface ServerSettingsApi {
    @GET("api/settings")
    suspend fun settings(): ServerSettingsDto

    @PUT("api/settings")
    suspend fun updateSettings(@Body body: ServerSettingsDto): SettingsUpdateResponseDto

    /**
     * The CALLING user's own content preferences — language order, 18+
     * visibility, download-all-chapters. Personal, unlike everything else on
     * this interface: no Owner check, and one user's choice never changes what
     * anyone else sees. Always comes back fully populated (the server fills
     * anything unset from its defaults), so there is nothing to merge here.
     */
    @GET("api/settings/content-preferences")
    suspend fun contentPreferences(): ContentPreferencesDto

    @PUT("api/settings/content-preferences")
    suspend fun updateContentPreferences(@Body body: ContentPreferencesDto): ContentPreferencesDto

    /** Languages derived from the installed sources. */
    @GET("api/settings/languages")
    suspend fun languages(): List<String>

    /** Uses the last SAVED SMTP settings, not whatever is on screen. */
    @POST("api/settings/test-email")
    suspend fun testEmail(@Body body: TestEmailRequestDto): TestEmailResponseDto
}
