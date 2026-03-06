package io.ogwars.cloud.paper.listener

import com.google.gson.Gson
import io.ogwars.cloud.api.event.CommandExecuteEvent
import io.ogwars.cloud.paper.kafka.KafkaManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

class CommandExecuteConsumer(
    private val plugin: JavaPlugin,
    private val kafkaManager: KafkaManager,
    private val serverId: String,
    private val groupName: String,
    private val logger: Logger
) {

    private val gson = Gson()
    private val consumerRunner = ManagedKafkaStringConsumer(
        kafkaManager = kafkaManager,
        groupId = "ogcloud-paper-command-$serverId",
        topic = TOPIC,
        threadName = "ogcloud-paper-command-consumer",
        clientIdSuffix = "command-consumer",
        autoOffsetReset = "latest",
        logger = logger,
        consumerLabel = "command execute",
        onRecord = ::processRecord
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

        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), event.command)
        })
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun CommandExecuteEvent.targetsThisServer(): Boolean {
        return when (targetType) {
            TARGET_SERVER -> target == serverId
            TARGET_GROUP -> target == groupName
            TARGET_ALL -> true
            else -> false
        }
    }

    companion object {
        private const val TOPIC = "ogcloud.command.execute"
        private const val TARGET_SERVER = "server"
        private const val TARGET_GROUP = "group"
        private const val TARGET_ALL = "all"
    }
}
