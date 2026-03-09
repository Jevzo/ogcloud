package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.api.event.NetworkUpdateEvent
import io.ogwars.cloud.paper.kafka.KafkaManager
import io.ogwars.cloud.paper.network.NetworkFeatureState
import com.google.gson.Gson
import java.util.logging.Logger

class NetworkUpdateConsumer(
    private val kafkaManager: KafkaManager,
    private val networkFeatureState: NetworkFeatureState,
    private val logger: Logger,
    private val onFeaturesChanged: (permissionSystemEnabled: Boolean, tablistEnabled: Boolean) -> Unit,
    serverId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-paper-network-$serverId",
            topic = TOPIC,
            threadName = "ogcloud-paper-network-consumer",
            clientIdSuffix = "network",
            autoOffsetReset = "earliest",
            logger = logger,
            consumerLabel = "network update",
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, NetworkUpdateEvent::class.java)
        handleNetworkUpdate(event)
    }

    private fun handleNetworkUpdate(event: NetworkUpdateEvent) {
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
            onFeaturesChanged(event.general.permissionSystemEnabled, event.general.tablistEnabled)
        }
    }

    companion object {
        private const val TOPIC = "ogcloud.network.update"
    }
}
