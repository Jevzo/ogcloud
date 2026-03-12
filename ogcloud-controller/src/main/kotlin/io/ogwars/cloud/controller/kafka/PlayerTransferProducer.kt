package io.ogwars.cloud.controller.kafka

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

    fun publishTransfer(event: PlayerTransferEvent) {
        log.info(
            "Publishing player transfer: serverId={}, target={}, reason={}",
            event.serverId,
            event.target,
            event.reason,
        )

        val key = event.serverId ?: event.playerUuid ?: "unknown"
        kafkaTemplate.send(KafkaTopics.PLAYER_TRANSFER, key, event)
    }
}
