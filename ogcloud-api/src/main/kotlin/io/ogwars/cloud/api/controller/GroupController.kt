package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.service.GroupService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/groups")
@Validated
class GroupController(
    private val groupService: GroupService,
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
    ): PaginatedResponse<GroupResponse> = groupService.listAll(query, page, size)

    @GetMapping("/{name}")
    fun getGroup(
        @PathVariable name: String,
    ): GroupResponse = groupService.getByName(name)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(
        @RequestBody @Valid request: CreateGroupRequest,
    ): GroupResponse = groupService.create(request)

    @PutMapping("/{name}")
    fun updateGroup(
        @PathVariable name: String,
        @RequestBody @Valid request: UpdateGroupRequest,
    ): GroupResponse = groupService.update(name, request)

    @PutMapping("/{name}/maintenance")
    fun toggleMaintenance(
        @PathVariable name: String,
        @RequestBody request: MaintenanceToggleRequest,
    ): GroupResponse = groupService.setMaintenance(name, request.maintenance)

    @PostMapping("/{name}/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun restartGroup(
        @PathVariable name: String,
    ) = groupService.requestRestart(name)

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGroup(
        @PathVariable name: String,
    ) = groupService.delete(name)
}
