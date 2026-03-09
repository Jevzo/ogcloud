package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.PlayerSwitchEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PlayerSwitchConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaConfig.PLAYER_SWITCH], groupId = "ogcloud-controller")
    fun onPlayerSwitch(message: String) {
        try {
            val event = objectMapper.readValue(message, PlayerSwitchEvent::class.java)
            playerTrackingService.handleSwitch(event)
        } catch (e: Exception) {
            log.error("Failed to process player switch event", e)
        }
    }
}
