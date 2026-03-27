package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.common.event.NetworkUpdateEvent
import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.model.TablistSettings
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.redis.RedisManager
import io.ogwars.cloud.velocity.tablist.TablistManager
import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class NetworkUpdateConsumer(
    private val kafkaManager: KafkaManager,
    private val networkState: NetworkState,
    private val permissionCache: PermissionCache,
    private val redisManager: RedisManager,
    private val proxyServer: ProxyServer,
    private val adminNotificationManager: AdminNotificationManager,
    private val tablistManager: TablistManager,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    proxyId: String,
) {
    private val gson = Gson()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-network-$proxyId",
            topic = KafkaTopics.NETWORK_UPDATE,
            threadName = "ogcloud-network-update-consumer",
            logger = logger,
            consumerLabel = "network update",
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
        val event = gson.fromJson(payload, NetworkUpdateEvent::class.java)
        applyNetworkUpdate(event)
    }

    private fun applyNetworkUpdate(event: NetworkUpdateEvent) {
        val general = event.general
        val wasMaintenanceEnabled = networkState.maintenance
        val wasPermissionSystemEnabled = networkState.permissionSystemEnabled

        updateNetworkState(event)

        logger.info(
            "Network state updated: maintenance={}, maxPlayers={}, defaultGroup={}, permissionSystemEnabled={}, tablistEnabled={}, proxyRoutingStrategy={}",
            event.maintenance,
            event.maxPlayers,
            event.defaultGroup,
            general.permissionSystemEnabled,
            general.tablistEnabled,
            general.proxyRoutingStrategy,
        )

        if (event.maintenance != wasMaintenanceEnabled) {
            adminNotificationManager.notifyNetworkMaintenance(event.maintenance)
        }

        if (event.maintenance && !wasMaintenanceEnabled) {
            kickNonBypassedPlayers(event.maintenanceKickMessage)
        }

        if (!general.permissionSystemEnabled && wasPermissionSystemEnabled) {
            permissionCache.clear()
        }

        if (general.permissionSystemEnabled && !wasPermissionSystemEnabled) {
            reloadPermissionCacheForOnlinePlayers()
        }

        if (general.tablistEnabled) {
            event.tablist?.let(::applyTablistUpdate)
        }
        tablistManager.setEnabled(general.tablistEnabled)
    }

    private fun updateNetworkState(event: NetworkUpdateEvent) {
        networkState.update(
            maintenance = event.maintenance,
            maintenanceKickMessage = event.maintenanceKickMessage,
            maxPlayers = event.maxPlayers,
            defaultGroup = event.defaultGroup,
            permissionSystemEnabled = event.general.permissionSystemEnabled,
            tablistEnabled = event.general.tablistEnabled,
            proxyRoutingStrategy = event.general.proxyRoutingStrategy,
        )
    }

    private fun kickNonBypassedPlayers(kickMessage: String) {
        val component = legacySerializer.deserialize(kickMessage)

        proxyServer.allPlayers.forEach { player ->
            val hasBypass =
                networkState.permissionSystemEnabled &&
                    permissionCache.hasPermission(
                        player.uniqueId,
                        MAINTENANCE_BYPASS_PERMISSION,
                    )

            if (!hasBypass) {
                player.disconnect(component)
            }
        }

        logger.info("Kicked non-bypassed players due to maintenance mode activation")
    }

    private fun applyTablistUpdate(tablist: TablistSettings) {
        tablistManager.headerTemplate = tablist.header
        tablistManager.footerTemplate = tablist.footer

        logger.info("Tablist template updated via network update")
    }

    private fun reloadPermissionCacheForOnlinePlayers() {
        proxyServer.allPlayers.forEach { player ->
            val session = redisManager.getPlayerData(player.uniqueId.toString())
            if (session != null) {
                permissionCache.cachePlayerFromRedis(player.uniqueId, session)
            } else {
                permissionCache.getDefaultGroup()?.let { defaultGroup ->
                    permissionCache.cachePlayer(player.uniqueId, defaultGroup, DEFAULT_PERMISSION_END_MILLIS)
                }
            }
        }

        logger.info(
            "Reloaded permission cache for {} online players after enabling permission system",
            proxyServer.playerCount,
        )
    }

    companion object {
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
        private const val DEFAULT_PERMISSION_END_MILLIS = -1L
    }
}
