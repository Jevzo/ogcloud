package io.ogwars.cloud.velocity.heartbeat

import io.ogwars.cloud.common.event.ProxyHeartbeatEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.velocity.kafka.KafkaSendDispatcher
import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ProxyHeartbeatTask(
    private val proxyServer: ProxyServer,
    private val kafkaSendDispatcher: KafkaSendDispatcher,
    private val proxyId: String,
    private val maxPlayers: Int,
    private val podIp: String,
    private val port: Int,
    private val logger: Logger,
) {
    private val gson = Gson()
    private lateinit var scheduler: ScheduledExecutorService

    fun start() {
        scheduler =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "ogcloud-proxy-heartbeat").apply { isDaemon = true }
            }
        scheduler.scheduleAtFixedRate(::sendHeartbeat, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS)
        logger.info(
            "Proxy heartbeat started (interval={}s, podIp={}, port={}, maxPlayers={})",
            HEARTBEAT_INTERVAL_SECONDS,
            podIp,
            port,
            maxPlayers,
        )
    }

    fun stop() {
        if (::scheduler.isInitialized) {
            scheduler.shutdown()
        }
    }

    private fun sendHeartbeat() {
        try {
            kafkaSendDispatcher.dispatch(
                KafkaSendDispatcher.Message(
                    topic = KafkaTopics.PROXY_HEARTBEAT,
                    key = proxyId,
                    payload = gson.toJson(createHeartbeatEvent()),
                    type = KafkaSendDispatcher.MessageType.PROXY_HEARTBEAT,
                ),
            )
        } catch (exception: Exception) {
            logger.error("Failed to send proxy heartbeat", exception)
        }
    }

    private fun createHeartbeatEvent(): ProxyHeartbeatEvent {
        val runtime = Runtime.getRuntime()
        return ProxyHeartbeatEvent(
            proxyId = proxyId,
            podIp = podIp,
            port = port,
            playerCount = proxyServer.playerCount,
            maxPlayers = maxPlayers,
            memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
        )
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_SECONDS = 10L
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}
