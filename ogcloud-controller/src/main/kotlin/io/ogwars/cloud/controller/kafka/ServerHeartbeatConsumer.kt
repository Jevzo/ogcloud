package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.ServerHeartbeatEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.ServerLifecycleService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ServerHeartbeatConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(topics = [KafkaConfig.SERVER_HEARTBEAT], groupId = "ogcloud-controller")
    fun onServerHeartbeat(message: String) {
        val event = objectMapper.readValue(message, ServerHeartbeatEvent::class.java)
        serverLifecycleService.handleHeartbeat(event)
    }
}
