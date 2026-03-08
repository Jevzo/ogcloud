package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.DashboardOverviewGroupResponse
import io.ogwars.cloud.api.dto.DashboardOverviewResponse
import io.ogwars.cloud.api.dto.DashboardOverviewScalingActionResponse
import io.ogwars.cloud.api.dto.DashboardOverviewStatsResponse
import io.ogwars.cloud.api.model.GroupDocument
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ScalingLogDocument
import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.api.redis.PlayerRedisRepository
import io.ogwars.cloud.api.redis.ServerRedisRepository
import io.ogwars.cloud.api.repository.GroupRepository
import io.ogwars.cloud.api.util.ServerPresentationSupport
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class DashboardService(
    private val networkService: NetworkService,
    private val groupRepository: GroupRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val serverRedisRepository: ServerRedisRepository,
    private val mongoTemplate: MongoTemplate,
) {
    fun getOverview(): DashboardOverviewResponse {
        val networkSettings = networkService.getSettings()
        val groupsById = groupRepository.findAll().associateBy { it.id }
        val servers = serverRedisRepository.findAll()
        val visibleInstances = servers.filter(::isVisibleInstance)

        return DashboardOverviewResponse(
            stats =
                DashboardOverviewStatsResponse(
                    totalPlayers = playerRedisRepository.findOnlinePlayerUuids().size,
                    maxPlayers = networkSettings.maxPlayers,
                    activeServers = servers.count(::isRunningServer),
                    maintenanceEnabled = networkSettings.maintenance,
                ),
            groups = buildGroupOverview(visibleInstances, groupsById, networkSettings.maxPlayers),
            scalingActions = buildScalingActions(),
        )
    }

    private fun buildGroupOverview(
        visibleInstances: List<ServerDocument>,
        groupsById: Map<String, GroupDocument>,
        defaultProxyMaxPlayers: Int,
    ): List<DashboardOverviewGroupResponse> {
        val groupIds = visibleInstances.map(ServerDocument::group).distinct()

        return groupIds
            .map { groupId ->
                val groupServers = visibleInstances.filter { it.group == groupId }
                val players = groupServers.sumOf(ServerDocument::playerCount)
                val totalCapacity =
                    groupServers.sumOf { server ->
                        ServerPresentationSupport.resolveMaxPlayers(
                            server,
                            groupsById[server.group]?.scaling?.playersPerServer ?: 0,
                            defaultProxyMaxPlayers,
                        )
                    }
                val inferredMode = groupServers.firstOrNull()?.type ?: DEFAULT_GROUP_MODE

                DashboardOverviewGroupResponse(
                    name = groupId,
                    mode = groupsById[groupId]?.type ?: inferredMode,
                    activeInstances = groupServers.size,
                    players = players,
                    capacityPercent = calculateCapacityPercent(players, totalCapacity),
                )
            }.sortedWith(
                compareByDescending<DashboardOverviewGroupResponse> { it.players }
                    .thenByDescending { it.activeInstances }
                    .thenBy { it.name },
            ).take(MAX_GROUPS)
    }

    private fun buildScalingActions(): List<DashboardOverviewScalingActionResponse> {
        val query =
            Query()
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(MAX_SCALING_ACTIONS)

        return mongoTemplate.find<ScalingLogDocument>(query).map { log ->
            DashboardOverviewScalingActionResponse(
                id = log.id,
                groupId = log.groupId,
                action = log.action,
                reason = log.reason,
                serverId = log.serverId,
                details = log.details,
                timestamp = log.timestamp,
            )
        }
    }

    private fun calculateCapacityPercent(
        players: Int,
        totalCapacity: Int,
    ): Double {
        if (totalCapacity <= 0) {
            return ZERO_CAPACITY_PERCENT
        }

        val rawPercent = players.toDouble() / totalCapacity.toDouble() * PERCENT_MULTIPLIER
        return (rawPercent * CAPACITY_PRECISION_FACTOR).roundToInt() / CAPACITY_PRECISION_FACTOR
    }

    private fun isRunningServer(server: ServerDocument): Boolean = server.state == ServerState.RUNNING

    private fun isVisibleInstance(server: ServerDocument): Boolean = server.state in VISIBLE_INSTANCE_STATES

    companion object {
        private const val CAPACITY_PRECISION_FACTOR = 10.0
        private const val MAX_GROUPS = 3
        private const val MAX_SCALING_ACTIONS = 5
        private const val PERCENT_MULTIPLIER = 100.0
        private const val ZERO_CAPACITY_PERCENT = 0.0
        private val DEFAULT_GROUP_MODE = GroupType.DYNAMIC
        private val VISIBLE_INSTANCE_STATES =
            setOf(
                ServerState.REQUESTED,
                ServerState.PREPARING,
                ServerState.STARTING,
                ServerState.RUNNING,
                ServerState.DRAINING,
            )
    }
}
