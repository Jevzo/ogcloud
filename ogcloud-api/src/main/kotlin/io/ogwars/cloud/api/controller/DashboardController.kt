package io.ogwars.cloud.api.controller

import io.ogwars.cloud.api.dto.DashboardOverviewResponse
import io.ogwars.cloud.api.service.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController(
    private val dashboardService: DashboardService
) {

    @GetMapping("/overview")
    fun getOverview(): DashboardOverviewResponse = dashboardService.getOverview()
}
