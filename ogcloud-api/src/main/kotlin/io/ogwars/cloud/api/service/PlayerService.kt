package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.exception.PermissionGroupNotFoundException
import io.ogwars.cloud.api.exception.PlayerNotFoundException
import io.ogwars.cloud.api.exception.PlayerNotOnlineException
import io.ogwars.cloud.api.kafka.PermissionUpdateProducer
import io.ogwars.cloud.api.kafka.PlayerTransferProducer
import io.ogwars.cloud.api.model.PermissionConfig
import io.ogwars.cloud.api.model.PlayerDocument
import io.ogwars.cloud.api.redis.PlayerRedisRepository
import io.ogwars.cloud.api.redis.ServerRedisRepository
import io.ogwars.cloud.api.repository.PermissionGroupRepository
import io.ogwars.cloud.api.repository.PlayerRepository
import io.ogwars.cloud.api.util.TimeUtils
import org.springframework.stereotype.Service

@Service
class PlayerService(
    private val playerRepository: PlayerRepository,
    private val permissionGroupRepository: PermissionGroupRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val serverRedisRepository: ServerRedisRepository,
    private val permissionUpdateProducer: PermissionUpdateProducer,
    private val playerTransferProducer: PlayerTransferProducer,
    private val networkService: NetworkService,
) {
    fun listOnlinePlayers(
        name: String?,
        serverId: String?,
        proxyId: String?,
        query: String?,
        page: Int,
        size: Int?,
    ): PaginatedResponse<OnlinePlayerResponse> {
        val onlineUuids = playerRedisRepository.findOnlinePlayerUuids()

        val players =
            onlineUuids
                .mapNotNull { uuid ->
                    val session = playerRedisRepository.findPlayerData(uuid) ?: return@mapNotNull null
                    val serverDisplayName = resolveDisplayName(session.serverId)
                    val proxyDisplayName = resolveDisplayName(session.proxyId)
                    val response = session.toOnlinePlayerResponse(uuid, serverDisplayName, proxyDisplayName)

                    if (name != null && !response.name.equals(name, ignoreCase = true)) return@mapNotNull null
                    if (serverId != null && response.serverId != serverId) return@mapNotNull null
                    if (proxyId != null && response.proxyId != proxyId) return@mapNotNull null

                    if (!PaginationSupport.matchesQuery(
                            query,
                            response.uuid,
                            response.name,
                            response.proxyId,
                            response.proxyDisplayName,
                            response.serverId,
                            response.serverDisplayName,
                            response.groupId,
                        )
                    ) {
                        return@mapNotNull null
                    }

                    response
                }.sortedWith(
                    compareByDescending<OnlinePlayerResponse> { it.connectedAt ?: "" }
                        .thenBy { it.name.lowercase() },
                )

        return PaginationSupport.paginate(players, page, size)
    }

    fun listPersistedPlayers(
        query: String?,
        page: Int,
        size: Int?,
    ): PaginatedResponse<PersistedPlayerResponse> {
        val players =
            playerRepository
                .findAll()
                .map { player ->
                    val session = playerRedisRepository.findPlayerData(player.id)
                    player.toPersistedResponse(session)
                }.filter { player ->
                    PaginationSupport.matchesQuery(
                        query,
                        player.uuid,
                        player.name,
                        player.permission.group,
                        player.proxyId,
                        player.serverId,
                    )
                }.sortedWith(
                    compareBy<PersistedPlayerResponse> { it.name.lowercase() }
                        .thenBy { it.uuid },
                )

        return PaginationSupport.paginate(players, page, size)
    }

    fun getPlayer(uuid: String): PlayerResponse {
        val player =
            playerRepository
                .findById(uuid)
                .orElseThrow { PlayerNotFoundException(uuid) }

        return buildPlayerResponse(player)
    }

    fun setPlayerGroup(
        uuid: String,
        request: SetPlayerGroupRequest,
    ): PlayerResponse {
        ensurePermissionSystemEnabled()

        val player =
            playerRepository
                .findById(uuid)
                .orElseThrow { PlayerNotFoundException(uuid) }

        val group =
            permissionGroupRepository
                .findById(request.group)
                .orElseThrow { PermissionGroupNotFoundException(request.group) }

        val durationMillis = TimeUtils.parseTimeString(request.duration)
        val endMillis =
            if (durationMillis == PERMANENT_PERMISSION_END_MILLIS) {
                PERMANENT_PERMISSION_END_MILLIS
            } else {
                System.currentTimeMillis() + durationMillis
            }
        val nextPermissionVersion = player.permission.version + 1

        val updated =
            player.copy(
                permission =
                    PermissionConfig(
                        group = request.group,
                        length = durationMillis,
                        endMillis = endMillis,
                        version = nextPermissionVersion,
                    ),
            )
        playerRepository.save(updated)

        permissionUpdateProducer.publishPermissionUpdate(
            uuid = uuid,
            group = group,
            permissionEndMillis = endMillis,
            permissionVersion = nextPermissionVersion,
            updatedBy = API_UPDATED_BY,
        )

        return buildPlayerResponse(updated)
    }

    fun transferPlayer(
        uuid: String,
        target: String,
    ) {
        if (!playerRedisRepository.isOnline(uuid)) {
            throw PlayerNotOnlineException(uuid)
        }

        playerTransferProducer.publishPlayerTransfer(uuid, target, API_TRANSFER_REASON)
    }

    private fun buildPlayerResponse(player: PlayerDocument): PlayerResponse {
        val sessionData = playerRedisRepository.findPlayerData(player.id)
        val serverDisplayName = resolveDisplayName(sessionData?.serverId)
        val proxyDisplayName = resolveDisplayName(sessionData?.proxyId)

        return player.toPlayerResponse(sessionData, serverDisplayName, proxyDisplayName)
    }

    private fun resolveDisplayName(serverId: String?): String? {
        if (serverId == null) return null
        return serverRedisRepository.findById(serverId)?.displayName
    }

    companion object {
        private const val PERMANENT_PERMISSION_END_MILLIS = -1L
        private const val API_UPDATED_BY = "api"
        private const val API_TRANSFER_REASON = "api-transfer"
    }

    private fun ensurePermissionSystemEnabled() {
        if (!networkService.isPermissionSystemEnabled()) {
            throw IllegalArgumentException("Permission system is disabled in network settings.")
        }
    }
}
