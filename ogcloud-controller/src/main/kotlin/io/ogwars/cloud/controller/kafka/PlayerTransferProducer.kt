package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.PlayerTransferEvent
import io.ogwars.cloud.controller.config.KafkaConfig
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PlayerTransferProducer(
    private val kafkaTemplate: KafkaTemplate<String, PlayerTransferEvent>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishTransfer(event: PlayerTransferEvent) {
        log.info(
            "Publishing player transfer: serverId={}, target={}, reason={}",
            event.serverId,
            event.target,
            event.reason
        )

        val key = event.serverId ?: event.playerUuid ?: "unknown"
        kafkaTemplate.send(KafkaConfig.PLAYER_TRANSFER, key, event)
    }
}
