package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.common.event.PermissionUpdateEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PermissionUpdateConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.PERMISSION_UPDATE],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onPermissionUpdate(message: String) {
        val event = objectMapper.readValue(message, PermissionUpdateEvent::class.java)
        playerTrackingService.handlePermissionUpdate(event)
    }
}
