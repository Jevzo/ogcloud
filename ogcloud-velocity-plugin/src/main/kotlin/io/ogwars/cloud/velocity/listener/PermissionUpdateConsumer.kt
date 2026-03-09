package io.ogwars.cloud.velocity.listener
import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import com.google.gson.Gson
import org.slf4j.Logger
import java.util.*

class PermissionUpdateConsumer(
    private val kafkaManager: KafkaManager,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val logger: Logger,
    proxyId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-permupdate-$proxyId",
            topic = KafkaTopics.PERMISSION_UPDATE,
            threadName = "ogcloud-perm-update-consumer",
            logger = logger,
            consumerLabel = "permission update",
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, PermissionUpdateEvent::class.java)
        handlePermissionUpdate(event)
    }

    private fun handlePermissionUpdate(event: PermissionUpdateEvent) {
        if (!networkState.permissionSystemEnabled) {
            return
        }

        val uuid = parseUuid(event.uuid) ?: return

        permissionCache.getPlayer(uuid) ?: return
        permissionCache.cachePlayerFromEvent(uuid, event)

        logger.info("Permission cache refreshed: uuid={}, groupId={}", event.uuid, event.groupId)
    }

    private fun parseUuid(rawUuid: String): UUID? =
        runCatching { UUID.fromString(rawUuid) }
            .onFailure {
                logger.warn(
                    "Received permission update with invalid uuid: {}",
                    rawUuid,
                )
            }.getOrNull()

    fun stop() {
        consumerRunner.stop()
    }

    companion object {
    }
}
