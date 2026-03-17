package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.security.AuthenticatedUser
import io.ogwars.cloud.api.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    @Operation(summary = "Authenticate a web user")
    fun login(
        @RequestBody @Valid request: LoginRequest,
    ) = authService.login(request.email, request.password)

    @PostMapping("/refresh")
    @Operation(summary = "Refresh an access token")
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest,
    ) = authService.refresh(request.refreshToken)

    @PostMapping("/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Revoke a refresh token",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun revokeRefreshToken(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: RefreshTokenRequest,
    ) = authService.revokeRefreshToken(currentUser, request.refreshToken)

    @PostMapping("/revoke-all")
    @Operation(
        summary = "Revoke all refresh tokens for the current user",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun revokeAllRefreshTokens(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
    ) = authService.revokeAllRefreshTokens(currentUser)

    @PutMapping("/me")
    @Operation(
        summary = "Update the authenticated user's profile",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun updateOwnProfile(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: SelfUpdateRequest,
    ) = authService.updateOwnProfile(currentUser, request)

    @PostMapping("/link/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Request an OTP to link a Minecraft account",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun requestLinkOtp(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: RequestLinkOtpRequest,
    ) = authService.requestAccountLinkOtp(currentUser, request.minecraftUsername)

    @PostMapping("/link/confirm")
    @Operation(
        summary = "Confirm a Minecraft account link with an OTP",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun confirmLinkOtp(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: ConfirmLinkOtpRequest,
    ) = authService.confirmAccountLinkOtp(currentUser, request.otp)
}
