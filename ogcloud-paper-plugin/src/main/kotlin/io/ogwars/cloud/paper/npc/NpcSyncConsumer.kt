package io.ogwars.cloud.paper.npc

import io.ogwars.cloud.common.event.NpcSyncEvent
import io.ogwars.cloud.common.event.NpcSyncOperation
import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.paper.kafka.KafkaManager
import io.ogwars.cloud.paper.listener.ManagedKafkaStringConsumer
import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class NpcSyncConsumer(
    private val plugin: JavaPlugin,
    private val kafkaManager: KafkaManager,
    private val npcManager: NpcManager,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    private val serverId: String,
    private val groupName: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-paper-npc-$serverId",
            topic = KafkaTopics.NPC_SYNC,
            threadName = "ogcloud-paper-npc-consumer",
            clientIdSuffix = "npc-consumer",
            autoOffsetReset = "latest",
            logger = logger,
            consumerLabel = "npc sync",
            consumerRecoverySettings = consumerRecoverySettings,
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun processRecord(payload: String): CompletableFuture<Unit> {
        val event = gson.fromJson(payload, NpcSyncEvent::class.java)
        if (event.group != groupName && event.npc?.group != groupName) {
            return CompletableFuture.completedFuture(Unit)
        }

        val completion = CompletableFuture<Unit>()

        try {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    runCatching {
                        when (event.operation) {
                            NpcSyncOperation.UPSERT -> event.npc?.let(npcManager::upsertManagedNpc)
                            NpcSyncOperation.DELETE -> event.npcId?.let(npcManager::removeManagedNpc)
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
}
