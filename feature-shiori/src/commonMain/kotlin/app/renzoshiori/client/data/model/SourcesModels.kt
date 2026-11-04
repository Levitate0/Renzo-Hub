package app.renzoshiori.client.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the Sources page — RenzoBackend ExtensionDto (api/provider/list) and
 * the extension version manager (api/extensions). Mirrors RenzoFrontend
 * src/lib/api/types.ts `Provider` / `ExtensionRepository` / `ExtensionEntry`
 * and services/extensionsService.ts.
 *
 * NOTE: `isInstaled` is misspelled in the API. Do NOT "fix" it — the typo is
 * the wire format (the web's sources/lib.ts carries the same warning).
 */
@Serializable
data class ExtensionSourceDto(
    val name: String = "",
    val lang: String = "",
)

@Serializable
data class ExtensionEntryDto(
    val id: String = "",
    val onlineRepositoryName: String = "",
    val onlineRepositoryId: String = "",
    val isLocal: Boolean = false,
    val name: String = "",
    val downloadUTC: String? = null,
    @SerialName("package") val packageName: String = "",
    val version: String = "",
    val nsfw: Boolean = false,
    val sources: List<ExtensionSourceDto> = emptyList(),
)

@Serializable
data class ExtensionRepositoryDto(
    val name: String = "",
    val id: String = "",
    val entries: List<ExtensionEntryDto> = emptyList(),
)

@Serializable
data class ProviderDto(
    @SerialName("package") val packageName: String = "",
    val name: String = "",
    val thumbnailUrl: String = "",
    val isStorage: Boolean = false,
    val isEnabled: Boolean = false,
    val isBroken: Boolean = false,
    val isDead: Boolean = false,
    /** sic — the API misspells "isInstalled". */
    val isInstaled: Boolean = false,
    /** True when the CURRENT user has this source enabled for their own Search/Browse/Add-series. */
    val isEnabledForMe: Boolean = false,
    val activeEntry: Int = 0,
    val autoUpdate: Boolean = false,
    val onlineRepositories: List<ExtensionRepositoryDto> = emptyList(),
)

@Serializable
data class ExtensionVersionInfoDto(
    val version: String = "",
    val isLocal: Boolean = false,
    val repositoryId: String = "",
)

@Serializable
data class ExtensionInfoDto(
    val name: String = "",
    val autoUpdate: Boolean = false,
    val activeVersion: String = "",
    val versions: List<ExtensionVersionInfoDto> = emptyList(),
)

/** POST /api/serie/apply-default-priority response. */
@Serializable
data class ApplyDefaultPriorityResultDto(
    val success: Boolean = false,
    val error: String? = null,
    val seriesConsidered: Int = 0,
    val seriesReordered: Int = 0,
    val seriesAdopted: Int = 0,
    val chaptersQueued: Int = 0,
)

/**
 * PUT /api/auth/me body used by the Default priority order tab. Only the
 * `preferences` blob is sent; every other field on UpdateUserDto is nullable
 * server-side and left untouched.
 */
@Serializable
data class UpdatePreferencesRequestDto(
    val preferences: String = "",
)

/** Slice of GET /api/settings — only the NSFW visibility the toolbar reads. */
@Serializable
data class NsfwSettingsDto(
    /** "AlwaysHide" | "HideByDefault" | "Show" (JsonStringEnumConverter server-side). */
    val nsfwVisibility: String = "HideByDefault",
)

// ── Source migration (api/migrate) ─────────────────────────────────────────
// Sources › Migrate (ui/sources/MigrateTab.kt). Mirrors RenzoBackend
// Services/Series/SourceMigrationService.cs (DTO region, camelCase) and
// Services/Downloads/SourceSpeedStats.cs; web twin: lib/api/types.ts Migration*.
// Chapter numbers (`latest`) are C# decimal: Double here, never Float.

/** Recent download record of one source (SourceSpeedDto). */
@Serializable
data class SourceSpeedDto(
    /** Downloads started in the window. */
    val attempts: Int = 0,
    /** Downloads that completed. */
    val downloads: Int = 0,
    /** Downloads / attempts, 0..1; null with no attempts. */
    val successRate: Double? = null,
    /** Median seconds per completed chapter; null with none completed. */
    val medianSeconds: Double? = null,
)

/** A source the user's series use, to migrate away from. */
@Serializable
data class MigrationFromDto(
    val provider: String = "",
    val language: String = "",
    val seriesCount: Int = 0,
    val disabledCount: Int = 0,
    val speed: SourceSpeedDto = SourceSpeedDto(),
)

/** A source to migrate to: one the user can search. */
@Serializable
data class MigrationTargetDto(
    /** The source's MihonProviderId — what the start request lists. */
    val id: String = "",
    val provider: String = "",
    val language: String = "",
    val speed: SourceSpeedDto = SourceSpeedDto(),
    /** Reliable lately: ticked by default. */
    val suggested: Boolean = false,
)

/** GET /api/migrate/sources. */
@Serializable
data class MigrationSourcesDto(
    val from: List<MigrationFromDto> = emptyList(),
    val targets: List<MigrationTargetDto> = emptyList(),
)

/**
 * POST /api/migrate body. No defaults on purpose: the network Json has
 * encodeDefaults = false, and a field equal to its default would be dropped.
 */
@Serializable
data class MigrationStartRequestDto(
    val from: String,
    val language: String?,
    /** Source ids to look on, best first. */
    val targets: List<String>,
    /** Only these series; every series on [from] when null. */
    val seriesIds: List<String>? = null,
)

@Serializable
data class MigrationApplyItemDto(
    val seriesId: String,
    val mihonId: String,
)

/** POST /api/migrate/{id}/apply body (no defaults: see [MigrationStartRequestDto]). */
@Serializable
data class MigrationApplyRequestDto(
    val items: List<MigrationApplyItemDto>,
    /** "disable" (switch the old source off per series) or "keep". */
    val oldSource: String,
)

/** One place a series was found on a target source. */
@Serializable
data class MigrationCandidateDto(
    val mihonId: String = "",
    val sourceId: String = "",
    val provider: String = "",
    /** The title the source gives it. */
    val title: String = "",
    /** The series' other name it was found under, when not its own title. */
    val foundAs: String? = null,
    val chapterCount: Int = 0,
    val latest: Double? = null,
    /** Share (0..1) of the old source's chapters, by whole number, this one lists. */
    val coverage: Double = 0.0,
    /** Its newest chapter is far past the series' own: maybe another series of the same name. */
    val suspect: Boolean = false,
)

@Serializable
data class MigrationItemDto(
    val seriesId: String = "",
    val title: String = "",
    val thumbnailUrl: String? = null,
    /** Chapters the old source lists, which a match is measured against. */
    val chapters: Int = 0,
    /** "pending" | "matched" | "none" | "migrated" | "skipped" | "failed". */
    val status: String = "pending",
    val message: String? = null,
    val match: MigrationCandidateDto? = null,
    val alternatives: List<MigrationCandidateDto> = emptyList(),
)

/** A migration run: GET /api/migrate/current, /api/migrate/{id}, and the start/apply answers. */
@Serializable
data class MigrationRunDto(
    val id: String = "",
    val from: String = "",
    /** "searching" | "ready" | "applying" | "done" | "failed". */
    val phase: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val items: List<MigrationItemDto> = emptyList(),
)

/** Mirrors RenzoBackend Models/Enums/EntryType.cs (serialized as a plain int). */
object ProviderPreferenceEntryType {
    const val COMBO_BOX = 0
    const val COMBO_CHECK_BOX = 1
    const val TEXT_BOX = 2
    const val SWITCH = 3
}
