package io.ogwars.cloud.paper.api

import io.ogwars.cloud.api.event.ServerReadyEvent
import io.ogwars.cloud.api.model.*
import io.ogwars.cloud.paper.gamestate.GameStateManager
import io.ogwars.cloud.paper.permission.CachedPermission
import io.ogwars.cloud.paper.permission.PermissionManager
import io.ogwars.cloud.paper.redis.RedisManager
import io.ogwars.cloud.server.api.OgCloudServerAPI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.logging.Logger

class OgCloudServerAPIImpl(
    private val serverId: String,
    private val groupName: String,
    private val groupType: String,
    private val redisManager: RedisManager,
    private val permissionManager: PermissionManager,
    private val gameStateManager: GameStateManager,
    private val apiClient: ApiClient,
    private val logger: Logger
) : OgCloudServerAPI {

    private val serverReadyListeners = CopyOnWriteArrayList<Consumer<ServerReadyEvent>>()
    private val resolvedGroupType = GroupType.valueOf(groupType)

    override fun getServerId(): String = serverId

    override fun getGroupName(): String = groupName

    override fun getGroupType(): GroupType = resolvedGroupType

    override fun getGameState(): GameState = gameStateManager.currentState

    override fun setGameState(state: GameState) = gameStateManager.setGameState(state)

    override fun getServers(): List<RunningServer> {
        val ids = redisManager.getServerIds()
        return ids.mapNotNull(::findRunningServer)
    }

    override fun getServersByGroup(group: String): List<RunningServer> {
        val ids = redisManager.getServerIdsByGroup(group)
        return ids.mapNotNull(::findRunningServer)
    }

    override fun getServer(id: String): RunningServer? {
        return findRunningServer(id)
    }

    override fun getServerByPlayer(uuid: UUID): RunningServer? {
        val session = redisManager.getPlayerData(uuid.toString()) ?: return null
        val playerServerId = session.serverId ?: return null
        return findRunningServer(playerServerId)
    }

    override fun findPlayer(uuid: UUID): PlayerInfo? {
        val session = redisManager.getPlayerData(uuid.toString()) ?: return null
        return session.toPlayerInfo(uuid)
    }

    override fun getPlayerGroup(uuid: UUID): PermissionGroup? {
        val cached = permissionManager.getCachedPlayer(uuid) ?: return null
        return cached.toPermissionGroup()
    }

    override fun requestServer(group: String): CompletableFuture<ServerInfo> {
        return apiClient.requestServer(group).thenApply { response ->
            ServerInfo(id = response.serverId, group = response.group, displayName = response.group)
        }
    }

    override fun transferPlayer(uuid: UUID, serverId: String): CompletableFuture<Void> {
        return apiClient.transferPlayer(uuid.toString(), serverId)
    }

    override fun transferPlayerToGroup(uuid: UUID, group: String): CompletableFuture<Void> {
        return apiClient.transferPlayer(uuid.toString(), group)
    }

    override fun forceTemplatePush(): CompletableFuture<Void> {
        return apiClient.forceTemplatePush(serverId)
    }

    override fun onServerReady(listener: Consumer<ServerReadyEvent>) {
        serverReadyListeners.add(listener)
    }

    fun fireServerReady(server: RunningServer) {
        val event = ServerReadyEvent(server)
        serverReadyListeners.forEach { listener ->
            try {
                listener.accept(event)
            } catch (e: Exception) {
                logger.severe("Error in server ready listener: ${e.message}")
            }
        }
    }

    private fun findRunningServer(serverKey: String): RunningServer? {
        val data = redisManager.getServerData(serverKey) ?: return null
        return data.toRunningServer()
    }

    private fun RedisPlayerSession.toPlayerInfo(uuid: UUID): PlayerInfo {
        return PlayerInfo(
            uuid = uuid,
            name = name,
            serverId = serverId,
            proxyId = proxyId,
            groupName = permission.group,
            permissions = permissions
        )
    }

    private fun CachedPermission.toPermissionGroup(): PermissionGroup {
        return PermissionGroup(
            id = groupId, name = groupName, display = DisplayConfig(
                chatPrefix = chatPrefix, chatSuffix = chatSuffix, nameColor = nameColor, tabPrefix = tabPrefix
            ), weight = weight, permissions = permissions
        )
    }
}
