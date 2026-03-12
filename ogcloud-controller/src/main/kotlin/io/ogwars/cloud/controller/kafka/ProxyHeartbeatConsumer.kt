package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.common.event.ProxyHeartbeatEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.ServerLifecycleService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ProxyHeartbeatConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [KafkaTopics.PROXY_HEARTBEAT],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onProxyHeartbeat(message: String) {
        val event = objectMapper.readValue(message, ProxyHeartbeatEvent::class.java)
        serverLifecycleService.handleProxyHeartbeat(event)
    }
}
