package io.ogwars.cloud.velocity.listener

import com.google.gson.Gson
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.api.event.PlayerConnectEvent
import io.ogwars.cloud.api.event.PlayerDisconnectEvent
import io.ogwars.cloud.api.event.PlayerSwitchEvent
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.mongo.MongoManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import java.util.UUID

class PlayerConnectionListener(
    private val kafkaManager: KafkaManager,
    private val mongoManager: MongoManager,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val serverRegistry: ServerRegistry,
    private val proxyServer: ProxyServer,
    private val proxyGroup: String,
    private val proxyMaxPlayers: Int,
    private val proxyId: String,
    private val logger: Logger
) {

    private val gson = Gson()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    @Subscribe
    fun onLogin(event: LoginEvent) {
        val player = event.player
        val uuid = player.uniqueId

        loadPermissions(uuid)

        val hasMaintenanceBypass = permissionCache.hasPermission(uuid, MAINTENANCE_BYPASS_PERMISSION)

        when {
            networkState.maintenance && !hasMaintenanceBypass -> {
                denyLogin(event, uuid, networkState.maintenanceKickMessage)
            }

            serverRegistry.isGroupInMaintenance(proxyGroup) && !hasMaintenanceBypass -> {
                denyLogin(event, uuid, PROXY_MAINTENANCE_MESSAGE)
            }

            proxyServer.playerCount >= proxyMaxPlayers -> {
                denyLogin(event, uuid, PROXY_FULL_MESSAGE)
            }

            else -> {
                publish(TOPIC_CONNECT, uuid.toString(), PlayerConnectEvent(uuid.toString(), player.username, proxyId))
            }
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val uuid = event.player.uniqueId

        permissionCache.removePlayer(uuid)

        publish(TOPIC_DISCONNECT, uuid.toString(), PlayerDisconnectEvent(uuid.toString(), proxyId))
    }

    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val uuid = event.player.uniqueId
        val serverId = event.server.serverInfo.name.substringAfter("-")
        val previousServerId = event.previousServer.orElse(null)?.serverInfo?.name?.substringAfter("-")

        publish(TOPIC_SWITCH, uuid.toString(), PlayerSwitchEvent(uuid.toString(), serverId, previousServerId))
    }

    private fun loadPermissions(uuid: UUID) {
        try {
            val playerDoc = mongoManager.findPlayer(uuid.toString())
            val defaultGroup = permissionCache.getDefaultGroup()
            val resolvedGroup = playerDoc?.let { mongoManager.findPermissionGroup(it.permission.group) }

            when {
                playerDoc != null && resolvedGroup != null -> {
                    permissionCache.cachePlayer(uuid, resolvedGroup, playerDoc.permission.endMillis)
                }

                defaultGroup != null -> {
                    permissionCache.cachePlayer(uuid, defaultGroup, DEFAULT_PERMISSION_END_MILLIS)
                }
            }
        } catch (exception: Exception) {
            logger.error("Failed to load permissions for player: uuid={}", uuid, exception)

            permissionCache.getDefaultGroup()?.let {
                permissionCache.cachePlayer(uuid, it, DEFAULT_PERMISSION_END_MILLIS)
            }
        }
    }

    private fun denyLogin(event: LoginEvent, uuid: UUID, message: String) {
        permissionCache.removePlayer(uuid)
        event.result = ResultedEvent.ComponentResult.denied(legacySerializer.deserialize(message))
    }

    private fun publish(topic: String, key: String, payload: Any) {
        kafkaManager.send(topic, key, gson.toJson(payload))
    }

    companion object {
        private const val TOPIC_CONNECT = "ogcloud.player.connect"
        private const val TOPIC_DISCONNECT = "ogcloud.player.disconnect"
        private const val TOPIC_SWITCH = "ogcloud.player.switch"
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
        private const val DEFAULT_PERMISSION_END_MILLIS = -1L
        private const val PROXY_MAINTENANCE_MESSAGE = "&cThis proxy group is in maintenance."
        private const val PROXY_FULL_MESSAGE = "&cThis proxy is full."
    }
}
