package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.common.event.GameStateUpdateEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.ServerLifecycleService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class GameStateConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopics.SERVER_GAMESTATE],
        groupId = "ogcloud-controller",
        containerFactory = "busyKafkaListenerFactory",
    )
    fun onGameStateUpdate(message: String) {
        val event = objectMapper.readValue(message, GameStateUpdateEvent::class.java)

        log.info("Gamestate update received: serverId={}, gamestate={}", event.serverId, event.gameState)

        serverLifecycleService.handleGameStateUpdate(event)
    }
}
