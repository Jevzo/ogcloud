package io.ogwars.cloud.controller.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.event.PlayerDisconnectEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.PlayerTrackingService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PlayerDisconnectConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaConfig.PLAYER_DISCONNECT], groupId = "ogcloud-controller")
    fun onPlayerDisconnect(message: String) {
        try {
            val event = objectMapper.readValue(message, PlayerDisconnectEvent::class.java)
            playerTrackingService.handleDisconnect(event)
        } catch (e: Exception) {
            log.error("Failed to process player disconnect event", e)
        }
    }
}
