package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.api.event.NetworkUpdateEvent
import io.ogwars.cloud.api.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.paper.kafka.KafkaManager
import io.ogwars.cloud.paper.network.NetworkFeatureState
import com.google.gson.Gson
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class NetworkUpdateConsumer(
    private val kafkaManager: KafkaManager,
    private val networkFeatureState: NetworkFeatureState,
    private val logger: Logger,
    private val onFeaturesChanged: (
        permissionSystemEnabled: Boolean,
        tablistEnabled: Boolean,
    ) -> CompletableFuture<Unit>,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    serverId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-paper-network-$serverId",
            topic = KafkaTopics.NETWORK_UPDATE,
            threadName = "ogcloud-paper-network-consumer",
            clientIdSuffix = "network",
            autoOffsetReset = "earliest",
            logger = logger,
            consumerLabel = "network update",
            consumerRecoverySettings = consumerRecoverySettings,
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun processRecord(payload: String): CompletableFuture<Unit> {
        val event = gson.fromJson(payload, NetworkUpdateEvent::class.java)
        return handleNetworkUpdate(event)
    }

    private fun handleNetworkUpdate(event: NetworkUpdateEvent): CompletableFuture<Unit> {
        val previousPermissionSystemEnabled = networkFeatureState.permissionSystemEnabled
        val previousTablistEnabled = networkFeatureState.tablistEnabled

        networkFeatureState.update(
            permissionSystemEnabled = event.general.permissionSystemEnabled,
            tablistEnabled = event.general.tablistEnabled,
        )

        logger.info(
            "Applied network feature update: permissionSystemEnabled=${event.general.permissionSystemEnabled}, tablistEnabled=${event.general.tablistEnabled}",
        )

        if (previousPermissionSystemEnabled != event.general.permissionSystemEnabled ||
            previousTablistEnabled != event.general.tablistEnabled
        ) {
            return onFeaturesChanged(event.general.permissionSystemEnabled, event.general.tablistEnabled)
        }

        return CompletableFuture.completedFuture(Unit)
    }
}
