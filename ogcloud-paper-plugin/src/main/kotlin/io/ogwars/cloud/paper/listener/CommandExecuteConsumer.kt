package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.api.event.CommandExecuteEvent
import io.ogwars.cloud.api.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.api.kafka.NonRetryableKafkaRecordException
import io.ogwars.cloud.paper.kafka.KafkaManager
import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class CommandExecuteConsumer(
    private val plugin: JavaPlugin,
    private val kafkaManager: KafkaManager,
    private val serverId: String,
    private val groupName: String,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-paper-command-$serverId",
            topic = KafkaTopics.COMMAND_EXECUTE,
            threadName = "ogcloud-paper-command-consumer",
            clientIdSuffix = "command-consumer",
            autoOffsetReset = "latest",
            logger = logger,
            consumerLabel = "command execute",
            consumerRecoverySettings = consumerRecoverySettings,
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    private fun processRecord(payload: String): CompletableFuture<Unit> {
        val event = gson.fromJson(payload, CommandExecuteEvent::class.java)
        return handleCommandExecute(event)
    }

    private fun handleCommandExecute(event: CommandExecuteEvent): CompletableFuture<Unit> {
        validateEvent(event)
        if (!event.targetsThisServer()) {
            return CompletableFuture.completedFuture(Unit)
        }

        logger.info("Executing remote command: ${event.command}")

        val completion = CompletableFuture<Unit>()

        try {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    runCatching {
                        val executed = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), event.command)
                        if (!executed) {
                            throw NonRetryableKafkaRecordException(
                                "Paper command execution returned false: ${event.command}",
                            )
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

    fun stop() {
        consumerRunner.stop()
    }

    private fun CommandExecuteEvent.targetsThisServer(): Boolean =
        when (targetType) {
            TARGET_SERVER -> target == serverId
            TARGET_GROUP -> target == groupName
            TARGET_ALL -> true
            else -> throw NonRetryableKafkaRecordException("Unsupported command targetType: $targetType")
        }

    private fun validateEvent(event: CommandExecuteEvent) {
        if (event.command.isBlank()) {
            throw NonRetryableKafkaRecordException("Command execute event command must not be blank")
        }

        when (event.targetType) {
            TARGET_SERVER,
            TARGET_GROUP,
            TARGET_ALL,
            -> Unit
            else -> throw NonRetryableKafkaRecordException("Unsupported command targetType: ${event.targetType}")
        }

        if (event.targetType != TARGET_ALL && event.target.isBlank()) {
            throw NonRetryableKafkaRecordException("Command execute event target must not be blank")
        }
    }

    companion object {
        private const val TARGET_SERVER = "server"
        private const val TARGET_GROUP = "group"
        private const val TARGET_ALL = "all"
    }
}
