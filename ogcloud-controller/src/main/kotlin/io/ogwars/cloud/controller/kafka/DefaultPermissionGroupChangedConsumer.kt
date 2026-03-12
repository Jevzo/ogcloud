package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.common.event.DefaultPermissionGroupChangedEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DefaultPermissionGroupChangedConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.DEFAULT_PERMISSION_GROUP_CHANGED],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onDefaultPermissionGroupChanged(message: String) {
        val event = objectMapper.readValue(message, DefaultPermissionGroupChangedEvent::class.java)
        playerTrackingService.handleDefaultPermissionGroupChanged(event)
    }
}
