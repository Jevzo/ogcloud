package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.api.event.ServerLifecycleEvent
import io.ogwars.cloud.api.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.api.kafka.NonRetryableKafkaRecordException
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.RunningServer
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.paper.api.OgCloudServerAPIImpl
import io.ogwars.cloud.paper.kafka.KafkaManager
import com.google.gson.Gson
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class LifecycleConsumer(
    private val kafkaManager: KafkaManager,
    private val serverApi: OgCloudServerAPIImpl,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    serverId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-paper-lifecycle-$serverId",
            topic = KafkaTopics.SERVER_LIFECYCLE,
            threadName = "ogcloud-paper-lifecycle-consumer",
            clientIdSuffix = "lifecycle",
            autoOffsetReset = "latest",
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

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, ServerLifecycleEvent::class.java)
        event.toRunningServerOrNull()?.let(serverApi::fireServerReady)
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun ServerLifecycleEvent.toRunningServerOrNull(): RunningServer? {
        if (type == GroupType.PROXY || state != ServerState.RUNNING) {
            return null
        }

        val ip = podIp ?: throw NonRetryableKafkaRecordException("Running server lifecycle event is missing podIp")
        val port = port ?: throw NonRetryableKafkaRecordException("Running server lifecycle event is missing port")

        return RunningServer(
            id = this.serverId,
            group = this.group,
            type = this.type,
            displayName = displayName ?: this.serverId,
            state = ServerState.RUNNING,
            address = "$ip:$port",
        )
    }
}
