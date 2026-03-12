package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.common.event.PlayerTransferEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PlayerTransferProducer(
    private val kafkaTemplate: KafkaTemplate<String, PlayerTransferEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishPlayerTransfer(
        uuid: String,
        target: String,
        reason: String,
    ) {
        log.info("Publishing player transfer: uuid={}, target={}", uuid, target)

        kafkaTemplate.send(
            KafkaTopics.PLAYER_TRANSFER,
            uuid,
            PlayerTransferEvent(
                playerUuid = uuid,
                target = target,
                reason = reason,
            ),
        )
    }
}
