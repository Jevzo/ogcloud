package io.ogwars.cloud.velocity.listener

import com.google.gson.Gson
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.api.event.GroupUpdateEvent
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import net.kyori.adventure.text.Component
import org.slf4j.Logger

class GroupUpdateConsumer(
    private val kafkaManager: KafkaManager,
    private val serverRegistry: ServerRegistry,
    private val adminNotificationManager: AdminNotificationManager,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val proxyServer: ProxyServer,
    private val proxyGroup: String,
    private val logger: Logger,
    proxyId: String
) {

    private val gson = Gson()
    private val consumerRunner = ManagedKafkaStringConsumer(
        kafkaManager = kafkaManager,
        groupId = "ogcloud-velocity-group-$proxyId",
        topic = TOPIC,
        threadName = "ogcloud-group-update-consumer",
        logger = logger,
        consumerLabel = "group update",
        onRecord = ::processRecord
    )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, GroupUpdateEvent::class.java)
        applyGroupUpdate(event)
    }

    private fun applyGroupUpdate(event: GroupUpdateEvent) {
        val previousMaintenance = serverRegistry.isGroupInMaintenance(event.groupId)

        serverRegistry.setGroupMaintenance(event.groupId, event.maintenance)

        logger.info("Group update applied: groupId={}, maintenance={}", event.groupId, event.maintenance)

        if (event.maintenance != previousMaintenance) {
            adminNotificationManager.notifyGroupMaintenance(event.groupId, event.maintenance)
        }

        if (event.maintenance && !previousMaintenance) {
            enforceMaintenance(event)
        }
    }

    private fun enforceMaintenance(event: GroupUpdateEvent) {
        if (event.type == GroupType.PROXY) {
            enforceProxyMaintenance(event.groupId)
            return
        }

        val fallback = resolveFallback(event.groupId)

        serverRegistry.getPlayersInGroup(event.groupId).forEach { player ->
            if (hasMaintenanceBypass(player)) {
                return@forEach
            }

            if (fallback == null) {
                player.disconnect(Component.text(SERVER_MAINTENANCE_MESSAGE))

                logger.info(
                    "Kicked player {} because server group {} entered maintenance", player.username, event.groupId
                )
                return@forEach
            }

            player.createConnectionRequest(fallback).fireAndForget()

            logger.info(
                "Redirecting player {} from maintained group {} to {}",
                player.username,
                event.groupId,
                fallback.serverInfo.name
            )
        }
    }

    private fun enforceProxyMaintenance(groupId: String) {
        if (groupId != proxyGroup) {
            return
        }

        proxyServer.allPlayers.forEach { player ->
            if (hasMaintenanceBypass(player)) {
                return@forEach
            }

            player.disconnect(Component.text(PROXY_MAINTENANCE_MESSAGE))

            logger.info("Kicked player {} because proxy group {} entered maintenance", player.username, groupId)
        }
    }

    private fun resolveFallback(blockedGroup: String) =
        networkState.defaultGroup.takeIf { it != blockedGroup && !serverRegistry.isGroupInMaintenance(it) }
            ?.let { serverRegistry.getServersByGroup(it).minByOrNull { server -> server.playersConnected.size } }

    private fun hasMaintenanceBypass(player: Player): Boolean {
        if (!networkState.permissionSystemEnabled) {
            return false
        }

        return permissionCache.hasPermission(player.uniqueId, MAINTENANCE_BYPASS_PERMISSION)
    }

    companion object {
        private const val TOPIC = "ogcloud.group.update"
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
        private const val PROXY_MAINTENANCE_MESSAGE = "Proxy is in maintenance"
        private const val SERVER_MAINTENANCE_MESSAGE = "Server is in maintenance"
    }
}
