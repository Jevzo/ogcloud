package io.ogwars.cloud.paper.listener

import com.google.gson.Gson
import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.paper.kafka.KafkaManager
import io.ogwars.cloud.paper.permission.PermissionInjector
import io.ogwars.cloud.paper.permission.PermissionManager
import io.ogwars.cloud.paper.tablist.TablistTeamManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger

class PermissionUpdateConsumer(
    private val plugin: JavaPlugin,
    private val kafkaManager: KafkaManager,
    private val permissionManager: PermissionManager,
    private val tablistTeamManager: TablistTeamManager,
    private val logger: Logger,
    serverId: String
) {

    private val gson = Gson()
    private val consumerRunner = ManagedKafkaStringConsumer(
        kafkaManager = kafkaManager,
        groupId = "ogcloud-paper-permupdate-$serverId",
        topic = TOPIC,
        threadName = "ogcloud-paper-perm-update-consumer",
        clientIdSuffix = "consumer",
        autoOffsetReset = "earliest",
        logger = logger,
        consumerLabel = "permission update",
        onRecord = ::processRecord
    )

    fun start() {
        consumerRunner.start()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, PermissionUpdateEvent::class.java)
        handlePermissionUpdate(event)
    }

    private fun handlePermissionUpdate(event: PermissionUpdateEvent) {
        val uuid = parseUuid(event.uuid) ?: return
        val player = Bukkit.getPlayer(uuid) ?: return

        permissionManager.cachePlayerFromEvent(uuid, event)
        schedulePermissionRefresh(player)

        logger.info("Permission cache refreshed for player: uuid=${event.uuid}")
    }

    private fun schedulePermissionRefresh(player: Player) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            PermissionInjector.inject(player, permissionManager, logger)
            tablistTeamManager.refreshPlayer(player)
        })
    }

    private fun parseUuid(rawUuid: String): UUID? {
        return runCatching { UUID.fromString(rawUuid) }
            .onFailure { logger.warning("Received permission update with invalid uuid: $rawUuid") }
            .getOrNull()
    }

    fun stop() {
        consumerRunner.stop()
    }

    companion object {
        private const val TOPIC = "ogcloud.permission.update"
    }
}
