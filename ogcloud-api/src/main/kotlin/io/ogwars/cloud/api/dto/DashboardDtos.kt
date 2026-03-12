package io.ogwars.cloud.api.dto

import io.ogwars.cloud.common.model.GroupType
import java.time.Instant

data class DashboardOverviewResponse(
    val stats: DashboardOverviewStatsResponse,
    val groups: List<DashboardOverviewGroupResponse>,
    val scalingActions: List<DashboardOverviewScalingActionResponse>,
)

data class DashboardOverviewStatsResponse(
    val totalPlayers: Int,
    val maxPlayers: Int,
    val activeServers: Int,
    val maintenanceEnabled: Boolean,
)

data class DashboardOverviewGroupResponse(
    val name: String,
    val mode: GroupType,
    val activeInstances: Int,
    val players: Int,
    val capacityPercent: Double,
)

data class DashboardOverviewScalingActionResponse(
    val id: String?,
    val groupId: String,
    val action: String,
    val reason: String,
    val serverId: String?,
    val details: String?,
    val timestamp: Instant,
)
