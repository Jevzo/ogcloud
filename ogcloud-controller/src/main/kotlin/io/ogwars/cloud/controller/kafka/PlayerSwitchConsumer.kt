package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.common.event.PlayerSwitchEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PlayerSwitchConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.PLAYER_SWITCH],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onPlayerSwitch(message: String) {
        val event = objectMapper.readValue(message, PlayerSwitchEvent::class.java)
        playerTrackingService.handleSwitch(event)
    }
}
