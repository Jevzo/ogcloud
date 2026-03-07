package io.ogwars.cloud.velocity.listener

import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.api.event.NetworkUpdateEvent
import io.ogwars.cloud.api.model.TablistSettings
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.tablist.TablistManager
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger

class NetworkUpdateConsumer(
    private val kafkaManager: KafkaManager,
    private val networkState: NetworkState,
    private val permissionCache: PermissionCache,
    private val proxyServer: ProxyServer,
    private val adminNotificationManager: AdminNotificationManager,
    private val tablistManager: TablistManager,
    private val logger: Logger,
    proxyId: String
) {

    private val gson = Gson()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val consumerRunner = ManagedKafkaStringConsumer(
        kafkaManager = kafkaManager,
        groupId = "ogcloud-velocity-network-$proxyId",
        topic = TOPIC,
        threadName = "ogcloud-network-update-consumer",
        logger = logger,
        consumerLabel = "network update",
        onRecord = ::processRecord
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
        val wasMaintenanceEnabled = networkState.maintenance
        val wasPermissionSystemEnabled = networkState.permissionSystemEnabled

        updateNetworkState(event)

        logger.info(
            "Network state updated: maintenance={}, maxPlayers={}, defaultGroup={}, permissionSystemEnabled={}, tablistEnabled={}",
            event.maintenance,
            event.maxPlayers,
            event.defaultGroup,
            event.general.permissionSystemEnabled,
            event.general.tablistEnabled
        )

        if (event.maintenance != wasMaintenanceEnabled) {
            adminNotificationManager.notifyNetworkMaintenance(event.maintenance)
        }

        if (event.maintenance && !wasMaintenanceEnabled) {
            kickNonBypassedPlayers(event.maintenanceKickMessage)
        }

        if (!event.general.permissionSystemEnabled && wasPermissionSystemEnabled) {
            permissionCache.clear()
        }

        tablistManager.setEnabled(event.general.tablistEnabled)
        if (event.general.tablistEnabled) {
            event.tablist?.let(::applyTablistUpdate)
        }
    }

    private fun updateNetworkState(event: NetworkUpdateEvent) {
        networkState.update(
            maintenance = event.maintenance,
            maintenanceKickMessage = event.maintenanceKickMessage,
            maxPlayers = event.maxPlayers,
            defaultGroup = event.defaultGroup,
            permissionSystemEnabled = event.general.permissionSystemEnabled,
            tablistEnabled = event.general.tablistEnabled
        )
    }

    private fun kickNonBypassedPlayers(kickMessage: String) {
        val component = legacySerializer.deserialize(kickMessage)

        proxyServer.allPlayers.forEach { player ->
            val hasBypass = networkState.permissionSystemEnabled &&
                permissionCache.hasPermission(player.uniqueId, MAINTENANCE_BYPASS_PERMISSION)

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

    companion object {
        private const val TOPIC = "ogcloud.network.update"
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
    }
}
