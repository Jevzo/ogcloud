package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.common.event.TemplatePushEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class TemplatePushProducer(
    private val kafkaTemplate: KafkaTemplate<String, TemplatePushEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestPush(serverId: String) {
        log.info("Publishing template push request: serverId={}", serverId)

        kafkaTemplate.send(
            KafkaTopics.SERVER_TEMPLATE_PUSH,
            serverId,
            TemplatePushEvent(
                serverId = serverId,
            ),
        )
    }
}
