package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.PlayerConnectEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PlayerConnectConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.PLAYER_CONNECT],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onPlayerConnect(message: String) {
        val event = objectMapper.readValue(message, PlayerConnectEvent::class.java)
        playerTrackingService.handleConnect(event)
    }
}
