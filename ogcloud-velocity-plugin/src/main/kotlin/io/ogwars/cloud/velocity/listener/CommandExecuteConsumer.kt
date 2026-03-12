package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.api.event.CommandExecuteEvent
import io.ogwars.cloud.api.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.api.kafka.NonRetryableKafkaRecordException
import io.ogwars.cloud.velocity.kafka.KafkaManager
import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class CommandExecuteConsumer(
    private val kafkaManager: KafkaManager,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
    private val proxyId: String,
    private val groupName: String,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-command-$proxyId",
            topic = KafkaTopics.COMMAND_EXECUTE,
            threadName = "ogcloud-velocity-command-consumer",
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

        return proxyServer.commandManager
            .executeAsync(proxyServer.consoleCommandSource, event.command)
            .thenApply { executed ->
                if (!executed) {
                    throw NonRetryableKafkaRecordException(
                        "Velocity command execution returned false: ${event.command}",
                    )
                }
            }
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun CommandExecuteEvent.targetsThisServer(): Boolean =
        when (targetType) {
            TARGET_SERVER -> target == proxyId
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
