package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.common.event.PermissionGroupUpdatedEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PermissionGroupUpdatedConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.PERMISSION_GROUP_UPDATED],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onPermissionGroupUpdated(message: String) {
        val event = objectMapper.readValue(message, PermissionGroupUpdatedEvent::class.java)
        playerTrackingService.handlePermissionGroupUpdated(event)
    }
}
