package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.ServerHeartbeatEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.ServerLifecycleService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ServerHeartbeatConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.SERVER_HEARTBEAT],
        groupId = "ogcloud-controller",
        containerFactory = "busyKafkaListenerFactory",
    )
    fun onServerHeartbeat(message: String) {
        val event = objectMapper.readValue(message, ServerHeartbeatEvent::class.java)
        serverLifecycleService.handleHeartbeat(event)
    }
}
