package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerState

data class DashboardOverviewResponse(
    val stats: DashboardOverviewStatsResponse,
    val groups: List<DashboardOverviewGroupResponse>,
    val instances: List<DashboardOverviewInstanceResponse>
)

data class DashboardOverviewStatsResponse(
    val totalPlayers: Int,
    val maxPlayers: Int,
    val activeServers: Int,
    val maintenanceEnabled: Boolean
)

data class DashboardOverviewGroupResponse(
    val name: String,
    val mode: GroupType,
    val activeInstances: Int,
    val players: Int,
    val capacityPercent: Double
)

data class DashboardOverviewInstanceResponse(
    val id: String,
    val group: String,
    val state: ServerState,
    val tps: Double,
    val onlinePlayers: Int,
    val maxPlayers: Int
)
