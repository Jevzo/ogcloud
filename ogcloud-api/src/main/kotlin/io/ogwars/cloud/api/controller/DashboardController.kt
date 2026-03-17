package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.DashboardOverviewResponse
import io.ogwars.cloud.api.service.DashboardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
@SecurityRequirement(name = "bearerAuth")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    @GetMapping("/overview")
    @Operation(summary = "Get dashboard overview")
    fun getOverview(): DashboardOverviewResponse = dashboardService.getOverview()
}
