package io.ogwars.cloud.controller.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.event.ServerStopEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.ServerLifecycleService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ServerStopConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaConfig.SERVER_STOP], groupId = "ogcloud-controller")
    fun onServerStop(message: String) {
        val event = objectMapper.readValue(message, ServerStopEvent::class.java)

        log.info("Received server stop: serverId={}, reason={}", event.serverId, event.reason)

        serverLifecycleService.gracefulStop(event.serverId, event.reason)
    }
}
