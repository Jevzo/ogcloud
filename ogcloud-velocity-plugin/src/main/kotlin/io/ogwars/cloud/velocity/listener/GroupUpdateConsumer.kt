package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.common.event.GroupUpdateEvent
import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.google.gson.Gson
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class GroupUpdateConsumer(
    private val kafkaManager: KafkaManager,
    private val serverRegistry: ServerRegistry,
    private val adminNotificationManager: AdminNotificationManager,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val proxyServer: ProxyServer,
    private val proxyGroup: String,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    proxyId: String,
) {
    private val gson = Gson()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-group-$proxyId",
            topic = KafkaTopics.GROUP_UPDATE,
            threadName = "ogcloud-group-update-consumer",
            logger = logger,
            consumerLabel = "group update",
            consumerRecoverySettings = consumerRecoverySettings,
            onRecord = { payload ->
                processRecord(payload)
                CompletableFuture.completedFuture(Unit)
            },
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
                player.disconnect(
                    legacySerializer.deserialize(VelocityMessages.Listener.GroupUpdate.SERVER_MAINTENANCE),
                )

                logger.info(
                    "Kicked player {} because server group {} entered maintenance",
                    player.username,
                    event.groupId,
                )
                return@forEach
            }

            player.createConnectionRequest(fallback).fireAndForget()

            logger.info(
                "Redirecting player {} from maintained group {} to {}",
                player.username,
                event.groupId,
                fallback.serverInfo.name,
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

            player.disconnect(legacySerializer.deserialize(VelocityMessages.Listener.GroupUpdate.PROXY_MAINTENANCE))

            logger.info("Kicked player {} because proxy group {} entered maintenance", player.username, groupId)
        }
    }

    private fun resolveFallback(blockedGroup: String) =
        networkState.defaultGroup
            .takeIf { it != blockedGroup && !serverRegistry.isGroupInMaintenance(it) }
            ?.let { serverRegistry.getServersByGroup(it).minByOrNull { server -> server.playersConnected.size } }

    private fun hasMaintenanceBypass(player: Player): Boolean {
        if (!networkState.permissionSystemEnabled) {
            return false
        }

        return permissionCache.hasPermission(player.uniqueId, MAINTENANCE_BYPASS_PERMISSION)
    }

    companion object {
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
    }
}
