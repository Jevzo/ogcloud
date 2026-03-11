package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.CreatePermissionGroupRequest
import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.PermissionGroupResponse
import io.ogwars.cloud.api.dto.UpdatePermissionGroupRequest
import io.ogwars.cloud.api.service.PermissionGroupService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/permissions/groups")
@PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
@Validated
class PermissionGroupController(
    private val permissionGroupService: PermissionGroupService,
) {
    @GetMapping
    fun listGroups(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int,
        @RequestParam(
            required = false,
        ) @Min(
            1,
            message = "size must be greater than 0",
        ) @Max(200, message = "size must be less than or equal to 200") size: Int?,
    ): PaginatedResponse<PermissionGroupResponse> = permissionGroupService.listAll(query, page, size)

    @GetMapping("/{name}")
    fun getGroup(
        @PathVariable name: String,
    ): PermissionGroupResponse = permissionGroupService.getByName(name)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(
        @RequestBody @Valid request: CreatePermissionGroupRequest,
    ): PermissionGroupResponse = permissionGroupService.create(request)

    @PutMapping("/{name}")
    fun updateGroup(
        @PathVariable name: String,
        @RequestBody @Valid request: UpdatePermissionGroupRequest,
    ): PermissionGroupResponse = permissionGroupService.update(name, request)

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGroup(
        @PathVariable name: String,
    ) = permissionGroupService.delete(name)

    @PostMapping("/{name}/permissions/{perm}")
    fun addPermission(
        @PathVariable name: String,
        @PathVariable perm: String,
    ): PermissionGroupResponse = permissionGroupService.addPermission(name, perm)

    @DeleteMapping("/{name}/permissions/{perm}")
    fun removePermission(
        @PathVariable name: String,
        @PathVariable perm: String,
    ): PermissionGroupResponse = permissionGroupService.removePermission(name, perm)
}
