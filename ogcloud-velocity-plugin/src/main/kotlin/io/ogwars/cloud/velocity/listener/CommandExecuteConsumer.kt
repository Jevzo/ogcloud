package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.api.event.CommandExecuteEvent
import io.ogwars.cloud.velocity.kafka.KafkaManager
import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

class CommandExecuteConsumer(
    private val kafkaManager: KafkaManager,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
    private val proxyId: String,
    private val groupName: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-command-$proxyId",
            topic = TOPIC,
            threadName = "ogcloud-velocity-command-consumer",
            logger = logger,
            consumerLabel = "command execute",
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, CommandExecuteEvent::class.java)
        handleCommandExecute(event)
    }

    private fun handleCommandExecute(event: CommandExecuteEvent) {
        if (!event.targetsThisServer()) return

        logger.info("Executing remote command: ${event.command}")

        proxyServer.commandManager.executeAsync(proxyServer.consoleCommandSource, event.command)
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun CommandExecuteEvent.targetsThisServer(): Boolean =
        when (targetType) {
            TARGET_SERVER -> target == proxyId
            TARGET_GROUP -> target == groupName
            TARGET_ALL -> true
            else -> false
        }

    companion object {
        private const val TOPIC = "ogcloud.command.execute"
        private const val TARGET_SERVER = "server"
        private const val TARGET_GROUP = "group"
        private const val TARGET_ALL = "all"
    }
}
