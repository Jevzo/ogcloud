package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.NetworkUpdateEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.NetworkSettingsService
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NetworkUpdateConsumer(
    private val playerTrackingService: PlayerTrackingService,
    networkSettingsService: NetworkSettingsService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var previousPermissionSystemEnabled = networkSettingsService.findGlobal().general.permissionSystemEnabled

    @KafkaListener(
        topics = [KafkaTopics.NETWORK_UPDATE],
        groupId = "ogcloud-controller",
        containerFactory = "singleKafkaListenerFactory",
    )
    fun onNetworkUpdate(message: String) {
        val event = objectMapper.readValue(message, NetworkUpdateEvent::class.java)
        handleNetworkUpdate(event)
    }

    private fun handleNetworkUpdate(event: NetworkUpdateEvent) {
        val wasPermissionSystemEnabled = previousPermissionSystemEnabled
        val permissionSystemEnabled = event.general.permissionSystemEnabled
        previousPermissionSystemEnabled = permissionSystemEnabled
        playerTrackingService.updatePermissionSystemEnabled(permissionSystemEnabled)

        if (permissionSystemEnabled && !wasPermissionSystemEnabled) {
            playerTrackingService.handlePermissionSystemEnabled()
            log.info("Permission system re-enabled; refreshed online player permission sessions")
        }
    }
}
