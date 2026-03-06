package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.PaginationSupport
import io.ogwars.cloud.api.dto.ServerResponse
import io.ogwars.cloud.api.dto.toResponse
import io.ogwars.cloud.api.exception.ServerNotFoundException
import io.ogwars.cloud.api.kafka.ServerKillProducer
import io.ogwars.cloud.api.kafka.ServerRequestProducer
import io.ogwars.cloud.api.kafka.ServerStopProducer
import io.ogwars.cloud.api.kafka.TemplatePushProducer
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.api.redis.ServerRedisRepository
import io.ogwars.cloud.api.repository.GroupRepository
import io.ogwars.cloud.api.util.ServerPresentationSupport
import org.springframework.stereotype.Service

@Service
class ServerService(
    private val serverRedisRepository: ServerRedisRepository,
    private val groupRepository: GroupRepository,
    private val networkService: NetworkService,
    private val serverRequestProducer: ServerRequestProducer,
    private val serverStopProducer: ServerStopProducer,
    private val serverKillProducer: ServerKillProducer,
    private val templatePushProducer: TemplatePushProducer,
    private val auditLogService: AuditLogService
) {

    fun listAll(group: String?, query: String?, page: Int, size: Int?): PaginatedResponse<ServerResponse> {
        val servers = if (group != null) {
            serverRedisRepository.findByGroup(group)
        } else {
            serverRedisRepository.findAll()
        }

        val groupMaxPlayersById = groupRepository.findAll().associate { it.id to it.scaling.playersPerServer }
        val defaultProxyMaxPlayers = networkService.getSettings().maxPlayers

        val responses = servers.map { toResponse(it, groupMaxPlayersById, defaultProxyMaxPlayers) }
            .filter { server ->
                PaginationSupport.matchesQuery(
                    query,
                    server.id,
                    server.group,
                    server.type.name,
                    server.displayName,
                    server.state.name,
                    server.gameState,
                    server.podName,
                    server.podIp
                )
            }.sortedWith(
                compareByDescending<ServerResponse> { it.startedAt ?: "" }
                    .thenBy { it.id }
            )

        return PaginationSupport.paginate(responses, page, size)
    }

    fun getById(id: String): ServerResponse {
        val server = serverRedisRepository.findById(id)
            ?: throw ServerNotFoundException(id)

        val groupMaxPlayers = groupRepository.findById(server.group)
            .map { it.scaling.playersPerServer }
            .orElse(0)

        val defaultProxyMaxPlayers = networkService.getSettings().maxPlayers

        return toResponse(
            server,
            mapOf(server.group to groupMaxPlayers),
            defaultProxyMaxPlayers
        )
    }

    fun requestServer(group: String): String {
        val serverId = serverRequestProducer.requestServer(group)

        auditLogService.logApiAction(
            action = "SERVER_REQUESTED",
            targetType = "SERVER",
            targetId = serverId,
            summary = "Requested new server $serverId for group $group",
            metadata = mapOf("group" to group)
        )

        return serverId
    }

    fun stopServer(id: String) {
        requireServer(id)

        serverStopProducer.stopServer(id)

        auditLogService.logApiAction(
            action = "SERVER_STOP_REQUESTED",
            targetType = "SERVER",
            targetId = id,
            summary = "Requested graceful stop for server $id"
        )
    }

    fun killServer(id: String) {
        requireServer(id)

        serverKillProducer.killServer(id)

        auditLogService.logApiAction(
            action = "SERVER_KILL_REQUESTED",
            targetType = "SERVER",
            targetId = id,
            summary = "Requested hard kill for server $id"
        )
    }

    fun forceTemplatePush(id: String) {
        val server = serverRedisRepository.findById(id)
            ?: throw ServerNotFoundException(id)

        if (server.type == GroupType.PROXY) {
            throw IllegalStateException("Template push is not supported for proxy servers")
        }

        if (server.state != ServerState.RUNNING) {
            throw IllegalStateException("Server must be RUNNING to push template, current state: ${server.state}")
        }

        templatePushProducer.requestPush(id)

        auditLogService.logApiAction(
            action = "SERVER_TEMPLATE_PUSH_REQUESTED",
            targetType = "SERVER",
            targetId = id,
            summary = "Requested template push for server $id"
        )
    }

    private fun toResponse(
        server: ServerDocument,
        groupMaxPlayersById: Map<String, Int>,
        defaultProxyMaxPlayers: Int
    ): ServerResponse {
        return server.toResponse().copy(
            maxPlayers = ServerPresentationSupport.resolveMaxPlayers(
                server,
                groupMaxPlayersById[server.group] ?: 0,
                defaultProxyMaxPlayers
            ),
            tps = ServerPresentationSupport.resolveTps(server)
        )
    }

    private fun requireServer(id: String): ServerDocument {
        return serverRedisRepository.findById(id)
            ?: throw ServerNotFoundException(id)
    }
}
