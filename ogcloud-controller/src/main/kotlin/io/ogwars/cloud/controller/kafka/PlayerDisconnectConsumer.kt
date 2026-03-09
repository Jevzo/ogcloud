package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.PlayerDisconnectEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PlayerDisconnectConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.PLAYER_DISCONNECT],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onPlayerDisconnect(message: String) {
        val event = objectMapper.readValue(message, PlayerDisconnectEvent::class.java)
        playerTrackingService.handleDisconnect(event)
    }
}
