package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.api.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.api.kafka.NonRetryableKafkaRecordException
import io.ogwars.cloud.paper.kafka.KafkaManager
import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.PermissionInjector
import io.ogwars.cloud.paper.permission.PermissionManager
import io.ogwars.cloud.paper.tablist.TablistTeamManager
import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class PermissionUpdateConsumer(
    private val plugin: JavaPlugin,
    private val kafkaManager: KafkaManager,
    private val permissionManager: PermissionManager,
    private val tablistTeamManager: TablistTeamManager,
    private val networkFeatureState: NetworkFeatureState,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    serverId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-paper-permupdate-$serverId",
            topic = KafkaTopics.PERMISSION_UPDATE,
            threadName = "ogcloud-paper-perm-update-consumer",
            clientIdSuffix = "consumer",
            autoOffsetReset = "earliest",
            logger = logger,
            consumerLabel = "permission update",
            consumerRecoverySettings = consumerRecoverySettings,
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    private fun processRecord(payload: String): CompletableFuture<Unit> {
        val event = gson.fromJson(payload, PermissionUpdateEvent::class.java)
        return handlePermissionUpdate(event)
    }

    private fun handlePermissionUpdate(event: PermissionUpdateEvent): CompletableFuture<Unit> {
        if (!networkFeatureState.permissionSystemEnabled) {
            return CompletableFuture.completedFuture(Unit)
        }

        val uuid = parseUuid(event.uuid) ?: return CompletableFuture.completedFuture(Unit)
        val player = Bukkit.getPlayer(uuid) ?: return CompletableFuture.completedFuture(Unit)

        permissionManager.cachePlayerFromEvent(uuid, event)
        logger.info("Permission cache refreshed for player: uuid=${event.uuid}")
        return schedulePermissionRefresh(player)
    }

    private fun schedulePermissionRefresh(player: Player): CompletableFuture<Unit> {
        val completion = CompletableFuture<Unit>()

        try {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    runCatching {
                        PermissionInjector.inject(player, permissionManager, logger)
                        if (networkFeatureState.tablistEnabled) {
                            tablistTeamManager.refreshPlayer(player)
                        }
                    }.onSuccess {
                        completion.complete(Unit)
                    }.onFailure {
                        completion.completeExceptionally(it)
                    }
                },
            )
        } catch (exception: Exception) {
            completion.completeExceptionally(exception)
        }

        return completion
    }

    private fun parseUuid(rawUuid: String): UUID? =
        runCatching {
            UUID.fromString(rawUuid)
        }.getOrElse {
            throw NonRetryableKafkaRecordException("Received permission update with invalid uuid: $rawUuid", it)
        }

    fun stop() {
        consumerRunner.stop()
    }
}
