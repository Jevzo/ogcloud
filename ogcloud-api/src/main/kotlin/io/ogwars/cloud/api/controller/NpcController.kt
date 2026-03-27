package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.CreateNpcRequest
import io.ogwars.cloud.api.dto.NpcResponse
import io.ogwars.cloud.api.dto.UpdateNpcRequest
import io.ogwars.cloud.api.service.NpcService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
@RequestMapping("/api/v1/npcs")
@Validated
@Tag(name = "NPCs")
@SecurityRequirement(name = "bearerAuth")
class NpcController(
    private val npcService: NpcService,
) {
    @GetMapping
    @Operation(summary = "List managed NPCs")
    fun listNpcs(
        @RequestParam(required = false) group: String?,
    ): List<NpcResponse> = npcService.list(group)

    @GetMapping("/{id}")
    @Operation(summary = "Get a managed NPC")
    fun getNpc(
        @PathVariable id: String,
    ): NpcResponse = npcService.get(id)

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a managed NPC")
    fun createNpc(
        @RequestBody @Valid request: CreateNpcRequest,
    ): NpcResponse = npcService.create(request)

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    @Operation(summary = "Update a managed NPC")
    fun updateNpc(
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateNpcRequest,
    ): NpcResponse = npcService.update(id, request)

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a managed NPC")
    fun deleteNpc(
        @PathVariable id: String,
    ) = npcService.delete(id)
}
