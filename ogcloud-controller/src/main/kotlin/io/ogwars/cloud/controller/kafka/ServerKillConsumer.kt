package io.ogwars.cloud.controller.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.event.ServerKillEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.ServerLifecycleService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ServerKillConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaConfig.SERVER_KILL], groupId = "ogcloud-controller")
    fun onServerKill(message: String) {
        val event = objectMapper.readValue(message, ServerKillEvent::class.java)

        log.info("Received server kill: serverId={}, reason={}", event.serverId, event.reason)

        serverLifecycleService.killServer(event.serverId, event.reason)
    }
}
