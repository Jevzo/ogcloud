package io.ogwars.cloud.controller.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.PlayerTrackingService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PermissionUpdateConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaConfig.PERMISSION_UPDATE], groupId = "ogcloud-controller")
    fun onPermissionUpdate(message: String) {
        try {
            val event = objectMapper.readValue(message, PermissionUpdateEvent::class.java)
            playerTrackingService.handlePermissionUpdate(event)
        } catch (e: Exception) {
            log.error("Failed to process permission update event", e)
        }
    }
}
