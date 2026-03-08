package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.CreateWebUserRequest
import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.UpdateWebUserRequest
import io.ogwars.cloud.api.dto.WebUserResponse
import io.ogwars.cloud.api.service.WebUserService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/web/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
@Validated
class WebUserController(
    private val webUserService: WebUserService,
) {
    @GetMapping
    fun listUsers(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int,
        @RequestParam(
            required = false,
        ) @Min(
            1,
            message = "size must be greater than 0",
        ) @Max(200, message = "size must be less than or equal to 200") size: Int?,
    ): PaginatedResponse<WebUserResponse> = webUserService.listUsers(query, page, size)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(
        @RequestBody @Valid request: CreateWebUserRequest,
    ) = webUserService.createUser(request)

    @PutMapping("/{email}")
    fun updateUser(
        @PathVariable email: String,
        @RequestBody @Valid request: UpdateWebUserRequest,
    ) = webUserService.updateUser(email, request)

    @DeleteMapping("/{email}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(
        @PathVariable email: String,
    ) = webUserService.deleteUser(email)

    @DeleteMapping("/{email}/link")
    fun unlinkUserAccount(
        @PathVariable email: String,
    ) = webUserService.unlinkUserAccount(email)
}
