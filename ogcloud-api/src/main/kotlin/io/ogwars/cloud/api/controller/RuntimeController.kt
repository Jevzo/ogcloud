package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.RuntimeRefreshRequest
import io.ogwars.cloud.api.security.AuthenticatedUser
import io.ogwars.cloud.api.service.RuntimeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/runtime")
class RuntimeController(
    private val runtimeService: RuntimeService,
) {
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun refreshRuntime(
        @RequestBody @Valid request: RuntimeRefreshRequest,
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
    ) {
        runtimeService.requestRefresh(request.scope, currentUser.email)
    }
}
