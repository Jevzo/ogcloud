package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.common.event.PlayerTransferEvent
import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.kafka.NonRetryableKafkaRecordException
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.NpcTransferStrategy
import io.ogwars.cloud.common.model.ProxyRoutingStrategy
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.google.gson.Gson
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PlayerTransferConsumer(
    private val kafkaManager: KafkaManager,
    private val serverRegistry: ServerRegistry,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    proxyId: String,
) {
    private val gson = Gson()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val roundRobinCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-transfer-$proxyId",
            topic = KafkaTopics.PLAYER_TRANSFER,
            threadName = "ogcloud-transfer-consumer",
            logger = logger,
            consumerLabel = "player transfer",
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
        val event = gson.fromJson(payload, PlayerTransferEvent::class.java)
        val players = resolvePlayers(event) ?: return

        logger.info("Transferring {} player(s) (reason: {})", players.size, event.reason)

        players.forEach { player -> transferPlayer(player, event) }
    }

    private fun resolvePlayers(event: PlayerTransferEvent): List<Player>? {
        event.playerUuid?.let { rawUuid ->
            val playerUuid =
                runCatching { UUID.fromString(rawUuid) }
                    .getOrElse {
                        throw NonRetryableKafkaRecordException(
                            "Transfer target player UUID is invalid: $rawUuid",
                            it,
                        )
                    }

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

        throw NonRetryableKafkaRecordException("Transfer event has neither playerUuid nor serverId")
    }

    private fun transferPlayer(
        player: Player,
        event: PlayerTransferEvent,
    ) {
        val target = event.target

        if (target == null) {
            disconnectPlayer(player, VelocityMessages.Listener.PlayerTransfer.SHUTDOWN)
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

        val selected = selectTargetServer(target, event.routingStrategy)
        if (selected == null) {
            disconnectPlayer(player, VelocityMessages.Listener.PlayerTransfer.SHUTDOWN)
            logger.warn("No target servers in group '{}', kicking {}", target, player.username)
            return
        }

        player.createConnectionRequest(selected).fireAndForget()
        logger.info("Transferring player {} to {}", player.username, selected.serverInfo.name)
    }

    private fun transferToSpecificServer(
        player: Player,
        serverId: String,
    ): Boolean {
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

    private fun canAccessGroup(
        playerUuid: UUID,
        group: String,
    ): Boolean {
        if (!serverRegistry.isGroupInMaintenance(group)) {
            return true
        }

        if (!networkState.permissionSystemEnabled) {
            return false
        }

        return permissionCache.hasPermission(playerUuid, MAINTENANCE_BYPASS_PERMISSION)
    }

    private fun resolveDirectServerId(target: String): String? =
        if (serverRegistry.getServer(target) !=
            null
        ) {
            target
        } else {
            serverRegistry.findServerIdByDisplayName(target)
        }

    private fun selectTargetServer(
        group: String,
        routingStrategy: NpcTransferStrategy?,
    ) = selectTargetServer(group, routingStrategy, allowMaintenance = serverRegistry.isGroupInMaintenance(group))

    private fun selectTargetServer(
        group: String,
        routingStrategy: NpcTransferStrategy?,
        allowMaintenance: Boolean,
    ): RegisteredServer? {
        val servers =
            serverRegistry
                .getServersByGroup(group, includeMaintenance = allowMaintenance)
                .sortedBy { it.serverInfo.name }

        if (servers.isEmpty()) {
            return null
        }

        if (servers.size == 1 || serverRegistry.getGroupType(group) == GroupType.STATIC) {
            return servers.first()
        }

        if (routingStrategy != null) {
            return when (routingStrategy) {
                NpcTransferStrategy.MOST_FILLED -> servers.maxWithOrNull(compareBy { it.playersConnected.size })
                NpcTransferStrategy.LEAST_FILLED -> servers.minWithOrNull(compareBy { it.playersConnected.size })
                NpcTransferStrategy.BALANCED -> selectBalancedServer(servers)
            }
        }

        return when (networkState.proxyRoutingStrategy) {
            ProxyRoutingStrategy.LOAD_BASED -> selectBalancedServer(servers)
            ProxyRoutingStrategy.ROUND_ROBIN -> selectRoundRobinServer(group, servers)
        }
    }

    private fun selectBalancedServer(servers: List<RegisteredServer>): RegisteredServer? =
        servers.minWithOrNull(compareBy(::loadRatio).thenBy { it.serverInfo.name })

    private fun selectRoundRobinServer(
        group: String,
        servers: List<RegisteredServer>,
    ): RegisteredServer? {
        if (servers.isEmpty()) {
            return null
        }

        val nextIndex = roundRobinCounters.computeIfAbsent(group) { AtomicInteger(0) }.getAndIncrement()
        return servers[Math.floorMod(nextIndex, servers.size)]
    }

    private fun loadRatio(server: RegisteredServer): Double {
        val serverId = serverRegistry.findServerIdByRegistered(server) ?: return server.playersConnected.size.toDouble()
        val maxPlayers = serverRegistry.getMaxPlayers(serverId)?.takeIf { it > 0 } ?: return server.playersConnected.size.toDouble()
        return server.playersConnected.size.toDouble() / maxPlayers.toDouble()
    }

    private fun rerouteOrKickForMaintenance(
        player: Player,
        blockedGroup: String,
    ) {
        val defaultGroup = networkState.defaultGroup

        if (defaultGroup == blockedGroup || serverRegistry.isGroupInMaintenance(defaultGroup)) {
            disconnectPlayer(player, VelocityMessages.Listener.PlayerTransfer.MAINTENANCE)

            logger.info(
                "Kicked player {} because maintained group {} has no available fallback",
                player.username,
                blockedGroup,
            )
            return
        }

        val fallback =
            selectTargetServer(defaultGroup, routingStrategy = null, allowMaintenance = false)

        if (fallback == null) {
            disconnectPlayer(player, VelocityMessages.Listener.PlayerTransfer.NO_SERVERS)

            logger.warn(
                "No fallback server available in default group '{}' while redirecting {} from maintained group {}",
                defaultGroup,
                player.username,
                blockedGroup,
            )
            return
        }

        player.createConnectionRequest(fallback).fireAndForget()

        logger.info(
            "Redirecting player {} from maintained group {} to {}",
            player.username,
            blockedGroup,
            fallback.serverInfo.name,
        )
    }

    private fun disconnectPlayer(
        player: Player,
        message: String,
    ) {
        player.disconnect(legacySerializer.deserialize(message))
    }

    companion object {
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
    }
}
