package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.PermissionExpiryEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PermissionExpiryConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.PERMISSION_EXPIRY], groupId = "ogcloud-controller")
    fun onPermissionExpiry(message: String) {
        try {
            val event = objectMapper.readValue(message, PermissionExpiryEvent::class.java)
            playerTrackingService.handlePermissionExpiry(event)
        } catch (e: Exception) {
            log.error("Failed to process permission expiry event", e)
        }
    }
}
