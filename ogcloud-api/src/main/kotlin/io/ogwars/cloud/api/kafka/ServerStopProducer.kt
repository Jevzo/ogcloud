package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.config.KafkaConfig
import io.ogwars.cloud.api.event.ServerStopEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ServerStopProducer(
    private val kafkaTemplate: KafkaTemplate<String, ServerStopEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun stopServer(serverId: String) {
        log.info("Publishing server stop: serverId={}", serverId)

        kafkaTemplate.send(
            KafkaConfig.SERVER_STOP,
            serverId,
            ServerStopEvent(
                serverId = serverId,
                reason = "api-request",
            ),
        )
    }
}
