package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.security.AuthenticatedUser
import io.ogwars.cloud.api.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
    ) = authService.login(request.email, request.password)

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest,
    ) = authService.refresh(request.refreshToken)

    @PostMapping("/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeRefreshToken(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: RefreshTokenRequest,
    ) = authService.revokeRefreshToken(currentUser, request.refreshToken)

    @PostMapping("/revoke-all")
    fun revokeAllRefreshTokens(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
    ) = authService.revokeAllRefreshTokens(currentUser)

    @PutMapping("/me")
    fun updateOwnProfile(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: SelfUpdateRequest,
    ) = authService.updateOwnProfile(currentUser, request)

    @PostMapping("/link/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun requestLinkOtp(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: RequestLinkOtpRequest,
    ) = authService.requestAccountLinkOtp(currentUser, request.minecraftUsername)

    @PostMapping("/link/confirm")
    fun confirmLinkOtp(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestBody @Valid request: ConfirmLinkOtpRequest,
    ) = authService.confirmAccountLinkOtp(currentUser, request.otp)
}
