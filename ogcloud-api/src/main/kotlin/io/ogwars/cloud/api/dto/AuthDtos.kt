package io.ogwars.cloud.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,
    @field:NotBlank
    val password: String,
)

data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class AuthTokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: WebUserResponse,
)

data class RevokeAllTokensResponse(
    val revokedTokens: Int,
)

data class SelfUpdateRequest(
    @field:Email
    val email: String? = null,
    @field:Pattern(regexp = ".*\\S.*", message = "must not be blank")
    val password: String? = null,
)

data class RequestLinkOtpRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 16)
    val minecraftUsername: String,
)

data class ConfirmLinkOtpRequest(
    @field:Pattern(regexp = "^[0-9]{6}$", message = "must be a 6 digit code")
    val otp: String,
)
