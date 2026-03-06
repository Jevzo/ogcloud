package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.ServerRequestBody
import io.ogwars.cloud.api.dto.ServerRequestResponse
import io.ogwars.cloud.api.dto.ServerResponse
import io.ogwars.cloud.api.service.ServerService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/servers")
@Validated
class ServerController(
    private val serverService: ServerService
) {

    @GetMapping
    fun listServers(
        @RequestParam(required = false) group: String?,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int,
        @RequestParam(required = false) @Min(1, message = "size must be greater than 0") @Max(200, message = "size must be less than or equal to 200") size: Int?
    ): PaginatedResponse<ServerResponse> = serverService.listAll(group, query, page, size)

    @GetMapping("/{id}")
    fun getServer(
        @PathVariable id: String
    ): ServerResponse = serverService.getById(id)

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestServer(
        @RequestBody @Valid body: ServerRequestBody
    ): ServerRequestResponse {
        val serverId = serverService.requestServer(body.group)
        return ServerRequestResponse(serverId, body.group)
    }

    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun stopServer(
        @PathVariable id: String
    ) = serverService.stopServer(id)

    @PostMapping("/{id}/kill")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun killServer(
        @PathVariable id: String
    ) = serverService.killServer(id)

    @PostMapping("/{id}/template/push")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun forceTemplatePush(
        @PathVariable id: String
    ) = serverService.forceTemplatePush(id)
}
