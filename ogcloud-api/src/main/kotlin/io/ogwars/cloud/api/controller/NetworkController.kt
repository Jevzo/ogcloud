package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.MaintenanceToggleRequest
import io.ogwars.cloud.api.dto.NetworkLocksResponse
import io.ogwars.cloud.api.dto.NetworkSettingsResponse
import io.ogwars.cloud.api.dto.NetworkStatusResponse
import io.ogwars.cloud.api.dto.UpdateNetworkRequest
import io.ogwars.cloud.api.service.NetworkRestartService
import io.ogwars.cloud.api.service.NetworkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/network")
@Tag(name = "Network")
@SecurityRequirement(name = "bearerAuth")
class NetworkController(
    private val networkService: NetworkService,
    private val networkRestartService: NetworkRestartService,
) {
    @GetMapping
    @Operation(summary = "Get network settings")
    fun getSettings(): NetworkSettingsResponse = networkService.getSettings()

    @GetMapping("/status")
    @Operation(summary = "Get live network status")
    fun getStatus(): NetworkStatusResponse = networkService.getStatus()

    @GetMapping("/locks")
    @Operation(summary = "List active network synchronization locks")
    fun getActiveLocks(): NetworkLocksResponse = networkService.getActiveLocks()

    @PutMapping
    @Operation(summary = "Update network settings")
    fun updateSettings(
        @RequestBody @Valid request: UpdateNetworkRequest,
    ): NetworkSettingsResponse = networkService.updateSettings(request)

    @PutMapping("/maintenance")
    @Operation(summary = "Toggle network maintenance mode")
    fun toggleMaintenance(
        @RequestBody request: MaintenanceToggleRequest,
    ): NetworkSettingsResponse = networkService.setMaintenance(request.maintenance)

    @PostMapping("/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a full network restart")
    fun restartNetwork() = networkRestartService.requestRestart()
}
