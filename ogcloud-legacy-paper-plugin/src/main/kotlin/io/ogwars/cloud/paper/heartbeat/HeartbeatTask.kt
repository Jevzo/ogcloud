package io.ogwars.cloud.paper.heartbeat

import io.ogwars.cloud.common.event.ServerHeartbeatEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.paper.OgCloudPaperPlugin
import io.ogwars.cloud.paper.kafka.KafkaSendDispatcher
import com.google.gson.Gson

class HeartbeatTask(
    private val plugin: OgCloudPaperPlugin,
    private val kafkaSendDispatcher: KafkaSendDispatcher,
) {
    private val gson = Gson()
    private var taskId: Int = NO_TASK_ID

    fun start() {
        taskId =
            plugin.server.scheduler.scheduleSyncRepeatingTask(
                plugin,
                ::sendHeartbeat,
                0L,
                HEARTBEAT_INTERVAL_TICKS,
            )
        if (taskId == NO_TASK_ID) {
            plugin.logger.severe("Failed to schedule heartbeat task")
        }
    }

    private fun sendHeartbeat() {
        val heartbeatEvent = createHeartbeatEvent()
        plugin.server.scheduler.runTaskAsynchronously(
            plugin,
            Runnable {
                kafkaSendDispatcher.dispatch(
                    KafkaSendDispatcher.Message(
                        topic = KafkaTopics.SERVER_HEARTBEAT,
                        key = plugin.serverId,
                        payload = gson.toJson(heartbeatEvent),
                        type = KafkaSendDispatcher.MessageType.SERVER_HEARTBEAT,
                    ),
                )
            },
        )
    }

    fun stop() {
        if (taskId != NO_TASK_ID) {
            plugin.server.scheduler.cancelTask(taskId)
            taskId = NO_TASK_ID
        }
    }

    private fun createHeartbeatEvent(): ServerHeartbeatEvent {
        val runtime = Runtime.getRuntime()
        return ServerHeartbeatEvent(
            serverId = plugin.serverId,
            group = plugin.groupName,
            playerCount = plugin.server.onlinePlayers.size,
            maxPlayers = plugin.server.maxPlayers,
            tps = getTps(),
            memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
            gameState = plugin.gameStateManager.currentState.name,
        )
    }

    private fun getTps(): Double {
        val handle =
            plugin.server.javaClass
                .getMethod("getHandle")
                .invoke(plugin.server)
        val recentTpsField = handle.javaClass.getField("recentTps")

        val tps = recentTpsField.get(handle) as DoubleArray
        return tps[0].coerceAtMost(MAX_TPS)
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_TICKS = 100L
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val MAX_TPS = 20.0
        private const val NO_TASK_ID = -1
    }
}
