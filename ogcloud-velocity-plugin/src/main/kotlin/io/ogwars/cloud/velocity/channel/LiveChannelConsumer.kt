package io.ogwars.cloud.velocity.channel

import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.listener.ManagedKafkaStringConsumer
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.CompletableFuture

class LiveChannelConsumer(
    private val kafkaManager: KafkaManager,
    private val liveChannelManager: LiveChannelManager,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    proxyId: String,
) {
    private val consumerSessionId = UUID.randomUUID().toString()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-live-channel-$proxyId-$consumerSessionId",
            topic = KafkaTopics.LIVE_CHANNEL,
            threadName = "ogcloud-velocity-live-channel-consumer",
            autoOffsetReset = "latest",
            logger = logger,
            consumerLabel = "live channel",
            consumerRecoverySettings = consumerRecoverySettings,
            onRecord = { payload ->
                liveChannelManager.handleIncoming(payload)
                CompletableFuture.completedFuture(Unit)
            },
        )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }
}
