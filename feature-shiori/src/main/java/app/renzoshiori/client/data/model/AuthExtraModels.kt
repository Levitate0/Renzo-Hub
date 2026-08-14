package app.renzoshiori.client.data.model

import kotlinx.serialization.Serializable

/**
 * DTOs for the public password endpoints the web app's /auth pages use
 * (userService.forgotPassword / resetPassword / setPassword). Field names match
 * RenzoBackend's request records exactly — the backend binds camelCase JSON.
 *
 * Note on defaults: NetworkModule's Json is configured with
 * `encodeDefaults = false`, so a field left at its default value is omitted
 * from the request body. Every screen validates its inputs as non-blank before
 * sending, so the defaults below are never actually serialized away.
 */
@Serializable
data class ForgotPasswordRequestDto(
    val email: String = "",
)

@Serializable
data class ResetPasswordRequestDto(
    val token: String = "",
    val newPassword: String = "",
)

@Serializable
data class SetPasswordRequestDto(
    val username: String = "",
    val token: String = "",
    val password: String = "",
)

/** `{ success, message }` — forgot-password always returns the same generic body. */
@Serializable
data class GenericResultDto(
    val success: Boolean = false,
    val message: String? = null,
)

/** Every RenzoBackend failure body is `{ "error": "…" }` (see AuthController). */
@Serializable
data class ApiErrorDto(
    val error: String? = null,
)

/**
 * One remembered sign-in — a long-lived refresh session created by "Remember
 * me" or by approving a TV (GET /api/auth/devices; the web's
 * devices-section.tsx `RememberedDevice`). `isCurrent` is computed server-side
 * against the refresh cookie this client presented.
 */
@Serializable
data class RememberedDeviceDto(
    val id: String = "",
    val deviceName: String = "Existing session",
    val createdAt: String = "",
    val lastSeenAt: String = "",
    val expiresAt: String = "",
    val createdIp: String? = null,
    val isTvPairing: Boolean = false,
    val isCurrent: Boolean = false,
)
