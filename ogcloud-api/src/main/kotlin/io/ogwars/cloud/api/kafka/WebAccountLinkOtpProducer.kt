package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.common.event.WebAccountLinkOtpEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class WebAccountLinkOtpProducer(
    private val kafkaTemplate: KafkaTemplate<String, WebAccountLinkOtpEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishOtp(
        playerUuid: String,
        otp: String,
        requestedByEmail: String,
    ) {
        log.info("Publishing web account link OTP: playerUuid={}", playerUuid)

        kafkaTemplate.send(
            KafkaTopics.WEB_ACCOUNT_LINK_OTP,
            playerUuid,
            WebAccountLinkOtpEvent(
                playerUuid = playerUuid,
                otp = otp,
                requestedByEmail = requestedByEmail,
            ),
        )
    }
}
