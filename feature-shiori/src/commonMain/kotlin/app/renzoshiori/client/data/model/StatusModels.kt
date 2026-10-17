package app.renzoshiori.client.data.model

import kotlinx.serialization.Serializable

/**
 * DTOs for the Status page (RenzoBackend/Controllers/StatusController.cs).
 * Mirrors RenzoFrontend src/lib/api/types.ts SeriesHealth / ProviderHealth /
 * SmallProviderHealth / StatusSummary — the C# enums serialize as plain ints.
 */
object HealthStatusLevel {
    const val GREEN = 0
    const val YELLOW = 1
    const val RED = 2
}

object HealthStatusTargetType {
    const val SERIES = 0
    const val PROVIDER = 1
}

@Serializable
data class SmallProviderHealthDto(
    val providerId: String = "",
    val providerName: String = "",
    val language: String = "",
    val level: Int = 0,
)

@Serializable
data class SeriesHealthDto(
    val id: String = "",
    val title: String = "",
    val thumbnailUrl: String? = null,
    val level: Int = 0,
    val message: String = "",
    val lastChapterDate: String? = null,
    val daysWithoutRelease: Int? = null,
    /** Release cadence in days (absolute value, always positive). */
    val releaseCadenceDays: Int? = null,
    val providers: List<SmallProviderHealthDto> = emptyList(),
)

@Serializable
data class ProviderHealthDto(
    val providerId: String = "",
    val providerName: String = "",
    val scanlator: String = "",
    val language: String = "",
    val level: Int = 0,
    val message: String = "",
    val lastErrorDate: String? = null,
    val consecutiveErrors: Int = 0,
    val isMihonInstalled: Boolean = false,
    val affectedSeries: List<SeriesHealthDto> = emptyList(),
)

@Serializable
data class StatusSummaryDto(
    val totalYellowSeries: Int = 0,
    val totalRedSeries: Int = 0,
    val totalYellowProviders: Int = 0,
    val totalRedProviders: Int = 0,
)

/**
 * POST /api/status/clear body. The default targetType is deliberately -1 (an
 * impossible value): the shared Retrofit Json is configured with
 * `encodeDefaults = false`, so a 0 ("Series") would otherwise be dropped from
 * the payload instead of being sent explicitly.
 */
@Serializable
data class ClearAlertRequestDto(
    val targetType: Int = -1,
    val targetId: String = "",
)

/** PATCH /api/serie/{id}/cadence body — null clears the user override. */
@Serializable
data class SetCadenceRequestDto(
    val cadenceDays: Int? = null,
)

@Serializable
data class SetCadenceResponseDto(
    val releaseCadenceDays: Int? = null,
    val isUserSet: Boolean = false,
)

/**
 * GET /api/system/health (SystemHealthController, owner only) — mirrors
 * RenzoFrontend types.ts SystemHealth. Drives the Engine panel.
 */
@Serializable
data class SystemHealthDto(
    val engine: EngineHealthDto? = null,
    val downloads: DownloadQueueHealthDto = DownloadQueueHealthDto(),
    val background: BackgroundQueueHealthDto = BackgroundQueueHealthDto(),
)

@Serializable
data class EngineHealthDto(
    val running: Boolean = false,
    val responding: Boolean = false,
    val busyWorkers: Int? = null,
    val workers: Int? = null,
    val lastCompletedSecondsAgo: Long? = null,
    val heapUsedMb: Long? = null,
    val heapMaxMb: Long? = null,
    val browserHelpers: Int = 0,
    val browserHelperLimit: Int = 0,
    val zombieHelpers: Int = 0,
    val startedAt: String? = null,
    val restartsSinceAppStart: Int = 0,
    val lastRestartAt: String? = null,
    val lastRestartReason: String? = null,
)

@Serializable
data class DownloadQueueHealthDto(
    val slotsInUse: Int = 0,
    val maxSlots: Int = 0,
    val perSource: Int = 0,
    val waitingDue: Int = 0,
    val waitingLater: Int = 0,
    val running: Int = 0,
    val failed: Int = 0,
    val retries: Int = 0,
    val retryEveryMinutes: Int = 0,
    val autoDownloadNewChapters: Boolean = true,
)

@Serializable
data class BackgroundQueueHealthDto(
    val slotsInUse: Int = 0,
    val maxSlots: Int = 0,
)
