package io.ogwars.cloud.controller.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.api.event.ProxyHeartbeatEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.service.ServerLifecycleService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ProxyHeartbeatConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(topics = [KafkaConfig.PROXY_HEARTBEAT], groupId = "ogcloud-controller")
    fun onProxyHeartbeat(message: String) {
        val event = objectMapper.readValue(message, ProxyHeartbeatEvent::class.java)
        serverLifecycleService.handleProxyHeartbeat(event)
    }
}
