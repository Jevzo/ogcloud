package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.MaintenanceToggleRequest
import io.ogwars.cloud.api.dto.NetworkSettingsResponse
import io.ogwars.cloud.api.dto.NetworkStatusResponse
import io.ogwars.cloud.api.dto.UpdateNetworkRequest
import io.ogwars.cloud.api.service.NetworkService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/network")
class NetworkController(
    private val networkService: NetworkService,
) {
    @GetMapping
    fun getSettings(): NetworkSettingsResponse = networkService.getSettings()

    @GetMapping("/status")
    fun getStatus(): NetworkStatusResponse = networkService.getStatus()

    @PutMapping
    fun updateSettings(
        @RequestBody @Valid request: UpdateNetworkRequest,
    ): NetworkSettingsResponse = networkService.updateSettings(request)

    @PutMapping("/maintenance")
    fun toggleMaintenance(
        @RequestBody request: MaintenanceToggleRequest,
    ): NetworkSettingsResponse = networkService.setMaintenance(request.maintenance)
}
