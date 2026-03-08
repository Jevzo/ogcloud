package io.ogwars.cloud.velocity.listener

import com.google.gson.Gson
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.api.event.PlayerTransferEvent
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.util.*

class PlayerTransferConsumer(
    private val kafkaManager: KafkaManager,
    private val serverRegistry: ServerRegistry,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val proxy: ProxyServer,
    private val logger: Logger,
    proxyId: String
) {

    private val gson = Gson()
    private val consumerRunner = ManagedKafkaStringConsumer(
        kafkaManager = kafkaManager,
        groupId = "ogcloud-velocity-transfer-$proxyId",
        topic = TOPIC,
        threadName = "ogcloud-transfer-consumer",
        logger = logger,
        consumerLabel = "player transfer",
        onRecord = ::processRecord
    )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, PlayerTransferEvent::class.java)
        val players = resolvePlayers(event) ?: return

        logger.info("Transferring {} player(s) (reason: {})", players.size, event.reason)

        players.forEach { player -> transferPlayer(player, event.target) }
    }

    private fun resolvePlayers(event: PlayerTransferEvent): List<Player>? {
        event.playerUuid?.let { rawUuid ->
            val playerUuid = runCatching { UUID.fromString(rawUuid) }.onFailure {
                logger.warn(
                    "Transfer target player UUID is invalid: {}", rawUuid
                )
            }.getOrNull() ?: return null

            val player = proxy.getPlayer(playerUuid).orElse(null)
            if (player == null) {
                logger.warn("Transfer target player not found on this proxy: uuid={}", rawUuid)
                return null
            }

            return listOf(player)
        }

        event.serverId?.let { serverId ->
            val players = serverRegistry.getPlayersOnServer(serverId)
            return players.takeIf { it.isNotEmpty() }?.toList()
        }

        logger.warn("Transfer event has neither playerUuid nor serverId")

        return null
    }

    private fun transferPlayer(player: Player, target: String?) {
        if (target == null) {
            disconnectPlayer(player, SHUTDOWN_MESSAGE)
            return
        }

        val directServerId = resolveDirectServerId(target)
        if (directServerId != null && transferToSpecificServer(player, directServerId)) {
            return
        }

        if (!canAccessGroup(player.uniqueId, target)) {
            rerouteOrKickForMaintenance(player, target)
            return
        }

        val selected = selectTargetServer(target)
        if (selected == null) {
            disconnectPlayer(player, SHUTDOWN_MESSAGE)
            logger.warn("No target servers in group '{}', kicking {}", target, player.username)
            return
        }

        player.createConnectionRequest(selected).fireAndForget()
        logger.info("Transferring player {} to {}", player.username, selected.serverInfo.name)
    }

    private fun transferToSpecificServer(player: Player, serverId: String): Boolean {
        val targetServer = serverRegistry.getServer(serverId) ?: return false
        val targetGroup = serverRegistry.getGroupForServer(serverId)

        if (targetGroup != null && !canAccessGroup(player.uniqueId, targetGroup)) {
            rerouteOrKickForMaintenance(player, targetGroup)
            return true
        }

        player.createConnectionRequest(targetServer).fireAndForget()

        val displayName = serverRegistry.getDisplayName(serverId) ?: targetServer.serverInfo.name
        logger.info("Transferring player {} to server {}", player.username, displayName)

        return true
    }

    private fun canAccessGroup(playerUuid: UUID, group: String): Boolean {
        if (!serverRegistry.isGroupInMaintenance(group)) {
            return true
        }

        if (!networkState.permissionSystemEnabled) {
            return false
        }

        return permissionCache.hasPermission(playerUuid, MAINTENANCE_BYPASS_PERMISSION)
    }

    private fun resolveDirectServerId(target: String): String? {
        return if (serverRegistry.getServer(target) != null) target else serverRegistry.findServerIdByDisplayName(target)
    }

    private fun selectTargetServer(group: String) = serverRegistry.getServersByGroup(
        group, includeMaintenance = serverRegistry.isGroupInMaintenance(group)
    ).minByOrNull { it.playersConnected.size }

    private fun rerouteOrKickForMaintenance(player: Player, blockedGroup: String) {
        val defaultGroup = networkState.defaultGroup

        if (defaultGroup == blockedGroup || serverRegistry.isGroupInMaintenance(defaultGroup)) {
            disconnectPlayer(player, MAINTENANCE_MESSAGE)

            logger.info(
                "Kicked player {} because maintained group {} has no available fallback", player.username, blockedGroup
            )
            return
        }

        val fallback = serverRegistry.getServersByGroup(defaultGroup).minByOrNull { it.playersConnected.size }

        if (fallback == null) {
            disconnectPlayer(player, NO_SERVERS_MESSAGE)

            logger.warn(
                "No fallback server available in default group '{}' while redirecting {} from maintained group {}",
                defaultGroup,
                player.username,
                blockedGroup
            )
            return
        }

        player.createConnectionRequest(fallback).fireAndForget()

        logger.info(
            "Redirecting player {} from maintained group {} to {}",
            player.username,
            blockedGroup,
            fallback.serverInfo.name
        )
    }

    private fun disconnectPlayer(player: Player, message: String) {
        player.disconnect(Component.text(message))
    }

    companion object {
        private const val TOPIC = "ogcloud.player.transfer"
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
        private const val SHUTDOWN_MESSAGE = "Server shutting down"
        private const val MAINTENANCE_MESSAGE = "Server is in maintenance"
        private const val NO_SERVERS_MESSAGE = "No available servers. Please try again later."
    }
}
