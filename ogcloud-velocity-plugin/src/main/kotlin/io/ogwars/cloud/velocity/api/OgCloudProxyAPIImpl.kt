package io.ogwars.cloud.velocity.api

import io.ogwars.cloud.common.channel.LiveChannelPayload
import io.ogwars.cloud.common.channel.LiveChannelSubscription
import io.ogwars.cloud.common.event.ServerReadyEvent
import io.ogwars.cloud.common.model.*
import io.ogwars.cloud.proxy.api.OgCloudProxyAPI
import io.ogwars.cloud.velocity.channel.LiveChannelManager
import io.ogwars.cloud.velocity.permission.CachedPlayer
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.redis.RedisManager
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

class OgCloudProxyAPIImpl(
    private val permissionCache: PermissionCache,
    private val apiClient: ApiClient,
    private val redisManager: RedisManager,
    private val liveChannelManager: LiveChannelManager,
    private val logger: Logger,
) : OgCloudProxyAPI {
    private val serverReadyListeners = CopyOnWriteArrayList<Consumer<ServerReadyEvent>>()

    override fun getServers(): List<RunningServer> {
        val ids = redisManager.getServerIds()
        return ids.mapNotNull(::findRunningServer)
    }

    override fun getServersByGroup(group: String): List<RunningServer> {
        val ids = redisManager.getServerIdsByGroup(group)
        return ids.mapNotNull(::findRunningServer)
    }

    override fun getServer(id: String): RunningServer? = findRunningServer(id)

    override fun getServerByPlayer(uuid: UUID): RunningServer? {
        val session = redisManager.getPlayerData(uuid.toString()) ?: return null
        val serverId = session.serverId ?: return null
        return findRunningServer(serverId)
    }

    override fun findPlayer(uuid: UUID): PlayerInfo? {
        val session = redisManager.getPlayerData(uuid.toString()) ?: return null
        return session.toPlayerInfo(uuid)
    }

    override fun getPlayerGroup(uuid: UUID): PermissionGroup? {
        val cached = permissionCache.getPlayer(uuid) ?: return null
        return cached.toPermissionGroup()
    }

    override fun requestServer(group: String): CompletableFuture<ServerInfo> =
        apiClient.requestServer(group).thenApply { response ->
            ServerInfo(id = response.serverId, group = response.group, displayName = response.group)
        }

    override fun transferPlayer(
        uuid: UUID,
        serverId: String,
    ): CompletableFuture<Void> = apiClient.transferPlayer(uuid.toString(), serverId)

    override fun transferPlayerToGroup(
        uuid: UUID,
        group: String,
    ): CompletableFuture<Void> = apiClient.transferPlayer(uuid.toString(), group)

    override fun <T : LiveChannelPayload> subscribe(
        channelName: String,
        payloadType: Class<T>,
        listener: Consumer<T>,
    ): LiveChannelSubscription = liveChannelManager.subscribe(channelName, payloadType, listener)

    override fun <T : LiveChannelPayload> publish(
        channelName: String,
        payload: T,
    ) {
        liveChannelManager.publish(channelName, payload)
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
                logger.error("Error in server ready listener", e)
            }
        }
    }

    private fun findRunningServer(serverId: String): RunningServer? {
        val data = redisManager.getServerData(serverId) ?: return null
        return data.toRunningServer()
    }

    private fun RedisPlayerSession.toPlayerInfo(uuid: UUID): PlayerInfo =
        PlayerInfo(
            uuid = uuid,
            name = name,
            serverId = serverId,
            proxyId = proxyId,
            groupName = permission.group,
            permissions = permissions,
        )

    private fun CachedPlayer.toPermissionGroup(): PermissionGroup =
        PermissionGroup(
            id = groupId,
            name = groupName,
            display =
                DisplayConfig(
                    chatPrefix = chatPrefix,
                    chatSuffix = chatSuffix,
                    nameColor = nameColor,
                    tabPrefix = tabPrefix,
                ),
            weight = weight,
            permissions = permissions,
        )
}
