package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.event.ServerRequestEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.*

@Component
class ServerRequestProducer(
    private val kafkaTemplate: KafkaTemplate<String, ServerRequestEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestServer(group: String): String {
        val serverId = UUID.randomUUID().toString().replace("-", "")

        log.info("Publishing server request: group={}, serverId={}", group, serverId)

        kafkaTemplate.send(
            KafkaTopics.SERVER_REQUEST,
            group,
            ServerRequestEvent(
                group = group,
                requestedBy = "api",
                serverId = serverId,
            ),
        )

        return serverId
    }
}
