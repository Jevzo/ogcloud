package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.config.KafkaConfig
import io.ogwars.cloud.api.event.WebAccountLinkOtpEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class WebAccountLinkOtpProducer(
    private val kafkaTemplate: KafkaTemplate<String, WebAccountLinkOtpEvent>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishOtp(playerUuid: String, otp: String, requestedByEmail: String) {
        log.info("Publishing web account link OTP: playerUuid={}", playerUuid)

        kafkaTemplate.send(
            KafkaConfig.WEB_ACCOUNT_LINK_OTP, playerUuid, WebAccountLinkOtpEvent(
                playerUuid = playerUuid,
                otp = otp,
                requestedByEmail = requestedByEmail
            )
        )
    }
}
