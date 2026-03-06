package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.config.KafkaConfig
import io.ogwars.cloud.api.event.ServerKillEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ServerKillProducer(
    private val kafkaTemplate: KafkaTemplate<String, ServerKillEvent>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun killServer(serverId: String, reason: String = "api-kill") {
        log.info("Publishing server kill: serverId={}, reason={}", serverId, reason)

        kafkaTemplate.send(
            KafkaConfig.SERVER_KILL, serverId, ServerKillEvent(
                serverId = serverId,
                reason = reason
            )
        )
    }
}
