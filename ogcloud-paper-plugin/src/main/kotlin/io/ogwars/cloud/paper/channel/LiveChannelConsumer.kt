package io.ogwars.cloud.paper.channel

import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.paper.kafka.KafkaManager
import io.ogwars.cloud.paper.listener.ManagedKafkaStringConsumer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class LiveChannelConsumer(
    private val kafkaManager: KafkaManager,
    private val liveChannelManager: LiveChannelManager,
    private val logger: Logger,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    serverId: String,
) {
    private val consumerSessionId = UUID.randomUUID().toString()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-paper-live-channel-$serverId-$consumerSessionId",
            topic = KafkaTopics.LIVE_CHANNEL,
            threadName = "ogcloud-paper-live-channel-consumer",
            clientIdSuffix = "live-channel",
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
