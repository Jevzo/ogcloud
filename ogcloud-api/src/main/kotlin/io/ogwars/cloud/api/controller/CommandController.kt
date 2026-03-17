package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.CommandExecuteRequest
import io.ogwars.cloud.api.kafka.CommandExecuteProducer
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/command")
@Tag(name = "Commands")
@SecurityRequirement(name = "bearerAuth")
class CommandController(
    private val commandExecuteProducer: CommandExecuteProducer,
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Execute a command against a server, group, or the whole network")
    fun executeCommand(
        @RequestBody @Valid request: CommandExecuteRequest,
    ) = commandExecuteProducer.publishCommand(request.target, request.targetType, request.command)
}
