package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.DashboardOverviewGroupResponse
import io.ogwars.cloud.api.dto.DashboardOverviewInstanceResponse
import io.ogwars.cloud.api.dto.DashboardOverviewResponse
import io.ogwars.cloud.api.dto.DashboardOverviewStatsResponse
import io.ogwars.cloud.api.model.GroupDocument
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.api.redis.PlayerRedisRepository
import io.ogwars.cloud.api.redis.ServerRedisRepository
import io.ogwars.cloud.api.repository.GroupRepository
import io.ogwars.cloud.api.util.ServerPresentationSupport
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.roundToInt

@Service
class DashboardService(
    private val networkService: NetworkService,
    private val groupRepository: GroupRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val serverRedisRepository: ServerRedisRepository,
) {

    fun getOverview(): DashboardOverviewResponse {
        val networkSettings = networkService.getSettings()
        val groupsById = groupRepository.findAll().associateBy { it.id }
        val servers = serverRedisRepository.findAll()
        val visibleInstances = servers.filter(::isVisibleInstance)

        return DashboardOverviewResponse(
            stats = DashboardOverviewStatsResponse(
                totalPlayers = playerRedisRepository.findOnlinePlayerUuids().size,
                maxPlayers = networkSettings.maxPlayers,
                activeServers = servers.count(::isRunningServer),
                maintenanceEnabled = networkSettings.maintenance
            ),
            groups = buildGroupOverview(visibleInstances, groupsById, networkSettings.maxPlayers),
            instances = buildInstanceOverview(visibleInstances, groupsById, networkSettings.maxPlayers)
        )
    }

    private fun buildGroupOverview(
        visibleInstances: List<ServerDocument>,
        groupsById: Map<String, GroupDocument>,
        defaultProxyMaxPlayers: Int
    ): List<DashboardOverviewGroupResponse> {
        val groupIds = visibleInstances.map(ServerDocument::group).distinct()

        return groupIds.map { groupId ->
            val groupServers = visibleInstances.filter { it.group == groupId }
            val players = groupServers.sumOf(ServerDocument::playerCount)
            val totalCapacity = groupServers.sumOf { server ->
                ServerPresentationSupport.resolveMaxPlayers(
                    server,
                    groupsById[server.group]?.scaling?.playersPerServer ?: 0,
                    defaultProxyMaxPlayers
                )
            }
            val inferredMode = groupServers.firstOrNull()?.type ?: DEFAULT_GROUP_MODE

            DashboardOverviewGroupResponse(
                name = groupId,
                mode = groupsById[groupId]?.type ?: inferredMode,
                activeInstances = groupServers.size,
                players = players,
                capacityPercent = calculateCapacityPercent(players, totalCapacity)
            )
        }.sortedWith(
            compareByDescending<DashboardOverviewGroupResponse> { it.players }
                .thenByDescending { it.activeInstances }
                .thenBy { it.name }
        ).take(MAX_GROUPS)
    }

    private fun buildInstanceOverview(
        visibleInstances: List<ServerDocument>,
        groupsById: Map<String, GroupDocument>,
        defaultProxyMaxPlayers: Int
    ): List<DashboardOverviewInstanceResponse> {
        return visibleInstances
            .sortedByDescending(::resolveInstanceSortInstant)
            .take(MAX_INSTANCES)
            .map { server ->
                DashboardOverviewInstanceResponse(
                    id = server.id,
                    group = server.group,
                    state = server.state,
                    tps = ServerPresentationSupport.resolveTps(server),
                    onlinePlayers = server.playerCount,
                    maxPlayers = ServerPresentationSupport.resolveMaxPlayers(
                        server,
                        groupsById[server.group]?.scaling?.playersPerServer ?: 0,
                        defaultProxyMaxPlayers
                    )
                )
            }
    }

    private fun calculateCapacityPercent(players: Int, totalCapacity: Int): Double {
        if (totalCapacity <= 0) {
            return ZERO_CAPACITY_PERCENT
        }

        val rawPercent = players.toDouble() / totalCapacity.toDouble() * PERCENT_MULTIPLIER
        return (rawPercent * CAPACITY_PRECISION_FACTOR).roundToInt() / CAPACITY_PRECISION_FACTOR
    }

    private fun resolveInstanceSortInstant(server: ServerDocument): Instant {
        return server.startedAt ?: server.lastHeartbeat ?: Instant.EPOCH
    }

    private fun isRunningServer(server: ServerDocument): Boolean = server.state == ServerState.RUNNING

    private fun isVisibleInstance(server: ServerDocument): Boolean = server.state in VISIBLE_INSTANCE_STATES

    companion object {
        private const val CAPACITY_PRECISION_FACTOR = 10.0
        private const val MAX_GROUPS = 3
        private const val MAX_INSTANCES = 5
        private const val PERCENT_MULTIPLIER = 100.0
        private const val ZERO_CAPACITY_PERCENT = 0.0
        private val DEFAULT_GROUP_MODE = GroupType.DYNAMIC
        private val VISIBLE_INSTANCE_STATES = setOf(
            ServerState.REQUESTED,
            ServerState.PREPARING,
            ServerState.STARTING,
            ServerState.RUNNING,
            ServerState.DRAINING
        )
    }
}
