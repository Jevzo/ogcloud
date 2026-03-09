package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.DefaultPermissionGroupChangedEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DefaultPermissionGroupChangedConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.DEFAULT_PERMISSION_GROUP_CHANGED], groupId = "ogcloud-controller")
    fun onDefaultPermissionGroupChanged(message: String) {
        try {
            val event = objectMapper.readValue(message, DefaultPermissionGroupChangedEvent::class.java)
            playerTrackingService.handleDefaultPermissionGroupChanged(event)
        } catch (exception: Exception) {
            log.error("Failed to process default permission group changed event", exception)
        }
    }
}
