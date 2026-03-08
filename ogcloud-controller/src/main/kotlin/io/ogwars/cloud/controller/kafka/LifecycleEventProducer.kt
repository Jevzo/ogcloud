package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.ServerLifecycleEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class LifecycleEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, ServerLifecycleEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishStateChange(event: ServerLifecycleEvent) {
        log.info("Publishing lifecycle event: serverId={}, state={}", event.serverId, event.state)

        kafkaTemplate.send(KafkaConfig.SERVER_LIFECYCLE, event.serverId, event)
    }
}
