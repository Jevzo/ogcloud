package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.CommandExecuteRequest
import io.ogwars.cloud.api.kafka.CommandExecuteProducer
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/command")
class CommandController(
    private val commandExecuteProducer: CommandExecuteProducer,
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeCommand(
        @RequestBody @Valid request: CommandExecuteRequest,
    ) = commandExecuteProducer.publishCommand(request.target, request.targetType, request.command)
}
