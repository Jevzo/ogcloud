package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.PermissionGroupUpdatedEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PermissionGroupUpdatedConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaConfig.PERMISSION_GROUP_UPDATED], groupId = "ogcloud-controller")
    fun onPermissionGroupUpdated(message: String) {
        try {
            val event = objectMapper.readValue(message, PermissionGroupUpdatedEvent::class.java)
            playerTrackingService.handlePermissionGroupUpdated(event)
        } catch (exception: Exception) {
            log.error("Failed to process permission group updated event", exception)
        }
    }
}
