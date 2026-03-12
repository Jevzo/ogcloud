package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.common.event.ServerLifecycleEvent
import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.kafka.NonRetryableKafkaRecordException
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.RunningServer
import io.ogwars.cloud.common.model.ServerState
import io.ogwars.cloud.velocity.api.OgCloudProxyAPIImpl
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.notification.AdminNotificationManager
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.google.gson.Gson
import org.slf4j.Logger
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

class LifecycleConsumer(
    private val kafkaManager: KafkaManager,
    private val serverRegistry: ServerRegistry,
    private val adminNotificationManager: AdminNotificationManager,
    private val proxyApi: OgCloudProxyAPIImpl,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    proxyId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-lifecycle-$proxyId",
            topic = KafkaTopics.SERVER_LIFECYCLE,
            threadName = "ogcloud-lifecycle-consumer",
            logger = logger,
            consumerLabel = "lifecycle",
            consumerRecoverySettings = consumerRecoverySettings,
            onRecord = { payload ->
                processRecord(payload)
                CompletableFuture.completedFuture(Unit)
            },
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
        val podIp =
            event.podIp ?: throw NonRetryableKafkaRecordException(
                "Running server lifecycle event is missing podIp",
            )
        val port =
            event.port ?: throw NonRetryableKafkaRecordException(
                "Running server lifecycle event is missing port",
            )
        val runningServer =
            RunningServer(
                id = event.serverId,
                group = event.group,
                type = event.type,
                displayName = event.displayName ?: event.serverId,
                state = ServerState.RUNNING,
                address = "$podIp:$port",
            )

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

    companion object {
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
