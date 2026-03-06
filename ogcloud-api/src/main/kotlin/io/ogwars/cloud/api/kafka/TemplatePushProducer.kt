package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.config.KafkaConfig
import io.ogwars.cloud.api.event.TemplatePushEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class TemplatePushProducer(
    private val kafkaTemplate: KafkaTemplate<String, TemplatePushEvent>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun requestPush(serverId: String) {
        log.info("Publishing template push request: serverId={}", serverId)

        kafkaTemplate.send(
            KafkaConfig.SERVER_TEMPLATE_PUSH, serverId, TemplatePushEvent(
                serverId = serverId
            )
        )
    }
}
