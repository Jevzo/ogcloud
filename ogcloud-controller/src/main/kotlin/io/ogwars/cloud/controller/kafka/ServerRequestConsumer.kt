package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.ServerRequestEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.ServerLifecycleService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ServerRequestConsumer(
    private val serverLifecycleService: ServerLifecycleService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopics.SERVER_REQUEST],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onServerRequest(message: String) {
        val event = objectMapper.readValue(message, ServerRequestEvent::class.java)

        log.info(
            "Received server request: group={}, requestedBy={}, serverId={}",
            event.group,
            event.requestedBy,
            event.serverId,
        )

        serverLifecycleService.requestServer(event.group, event.requestedBy, event.serverId)
    }
}
