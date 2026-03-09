package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.PlayerConnectEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.PlayerTrackingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PlayerConnectConsumer(
    private val playerTrackingService: PlayerTrackingService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.PLAYER_CONNECT], groupId = "ogcloud-controller")
    fun onPlayerConnect(message: String) {
        try {
            val event = objectMapper.readValue(message, PlayerConnectEvent::class.java)
            playerTrackingService.handleConnect(event)
        } catch (e: Exception) {
            log.error("Failed to process player connect event", e)
        }
    }
}
