package io.ogwars.cloud.controller.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.event.NetworkUpdateEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.NetworkSettingsService
import io.ogwars.cloud.controller.service.PlayerTrackingService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NetworkUpdateConsumer(
    private val playerTrackingService: PlayerTrackingService,
    networkSettingsService: NetworkSettingsService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var previousPermissionSystemEnabled = networkSettingsService.findGlobal().general.permissionSystemEnabled

    @KafkaListener(topics = [KafkaConfig.NETWORK_UPDATE], groupId = "ogcloud-controller")
    fun onNetworkUpdate(message: String) {
        try {
            val event = objectMapper.readValue(message, NetworkUpdateEvent::class.java)
            handleNetworkUpdate(event)
        } catch (exception: Exception) {
            log.error("Failed to process network update event", exception)
        }
    }

    private fun handleNetworkUpdate(event: NetworkUpdateEvent) {
        val wasPermissionSystemEnabled = previousPermissionSystemEnabled
        previousPermissionSystemEnabled = event.general.permissionSystemEnabled

        if (event.general.permissionSystemEnabled && !wasPermissionSystemEnabled) {
            playerTrackingService.handleNetworkFeatureUpdate(permissionSystemEnabled = true)
            log.info("Permission system re-enabled; refreshed online player permission sessions")
        }
    }
}
