package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.OnlinePlayerResponse
import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.PersistedPlayerResponse
import io.ogwars.cloud.api.dto.PlayerResponse
import io.ogwars.cloud.api.dto.SetPlayerGroupRequest
import io.ogwars.cloud.api.dto.TransferPlayerRequest
import io.ogwars.cloud.api.service.PlayerService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
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
@RequestMapping("/api/v1/players")
@Validated
class PlayerController(
    private val playerService: PlayerService
) {

    @GetMapping
    fun listOnlinePlayers(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) serverId: String?,
        @RequestParam(required = false) proxyId: String?,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int, @RequestParam(required = false) @Min(1, message = "size must be greater than 0") @Max(200, message = "size must be less than or equal to 200") size: Int?
    ): PaginatedResponse<OnlinePlayerResponse> = playerService.listOnlinePlayers(name, serverId, proxyId, query, page, size)

    @GetMapping("/all")
    fun listPersistedPlayers(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") @Min(0, message = "page must be greater than or equal to 0") page: Int,
        @RequestParam(required = false) @Min(1, message = "size must be greater than 0") @Max(200, message = "size must be less than or equal to 200") size: Int?
    ): PaginatedResponse<PersistedPlayerResponse> = playerService.listPersistedPlayers(query, page, size)

    @GetMapping("/{uuid}")
    fun getPlayer(
        @PathVariable uuid: String
    ): PlayerResponse = playerService.getPlayer(uuid)

    @PutMapping("/{uuid}/group")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    fun setPlayerGroup(
        @PathVariable uuid: String,
        @RequestBody @Valid request: SetPlayerGroupRequest
    ): PlayerResponse = playerService.setPlayerGroup(uuid, request)

    @PostMapping("/{uuid}/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun transferPlayer(
        @PathVariable uuid: String,
        @RequestBody @Valid request: TransferPlayerRequest
    ) = playerService.transferPlayer(uuid, request.target)
}
