package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.api.event.ServerLifecycleEvent
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.RunningServer
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.velocity.api.OgCloudProxyAPIImpl
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.google.gson.Gson
import org.slf4j.Logger
import java.net.InetSocketAddress

class LifecycleConsumer(
    private val kafkaManager: KafkaManager,
    private val serverRegistry: ServerRegistry,
    private val adminNotificationManager: AdminNotificationManager,
    private val proxyApi: OgCloudProxyAPIImpl,
    private val logger: Logger,
    proxyId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-lifecycle-$proxyId",
            topic = TOPIC,
            threadName = "ogcloud-lifecycle-consumer",
            logger = logger,
            consumerLabel = "lifecycle",
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, ServerLifecycleEvent::class.java)
        if (event.type == GroupType.PROXY) {
            return
        }

        when (event.state) {
            ServerState.RUNNING -> registerRunningServer(event)
            ServerState.DRAINING -> serverRegistry.markDraining(event.serverId)
            ServerState.STOPPING, ServerState.STOPPED -> serverRegistry.unregisterServer(event.serverId)
            else -> Unit
        }

        if (event.state in NOTIFY_STATES) {
            adminNotificationManager.notifyServerLifecycle(event.displayName, event.state.name, event.group)
        }
    }

    private fun registerRunningServer(event: ServerLifecycleEvent) {
        val runningServer = event.toRunningServerOrNull() ?: return
        val podIp = event.podIp ?: return
        val port = event.port ?: return

        serverRegistry.registerServer(
            serverId = event.serverId,
            group = event.group,
            address = InetSocketAddress(podIp, port),
            displayName = event.displayName ?: defaultDisplayName(event),
        )

        proxyApi.fireServerReady(runningServer)
    }

    private fun defaultDisplayName(event: ServerLifecycleEvent): String {
        val shortServerId = event.serverId.take(6)
        return "${event.group}-$shortServerId"
    }

    private fun ServerLifecycleEvent.toRunningServerOrNull(): RunningServer? {
        val podIp = podIp ?: return null
        val port = port ?: return null

        return RunningServer(
            id = serverId,
            group = group,
            type = type,
            displayName = displayName ?: serverId,
            state = ServerState.RUNNING,
            address = "$podIp:$port",
        )
    }

    companion object {
        private const val TOPIC = "ogcloud.server.lifecycle"
        private val NOTIFY_STATES =
            setOf(
                ServerState.REQUESTED,
                ServerState.STARTING,
                ServerState.RUNNING,
                ServerState.DRAINING,
                ServerState.STOPPED,
            )
    }
}
