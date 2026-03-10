package io.ogwars.cloud.velocity.listener
import io.ogwars.cloud.api.event.WebAccountLinkOtpEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.velocity.command.OgCloudCommand
import io.ogwars.cloud.velocity.kafka.KafkaManager
import io.ogwars.cloud.velocity.message.VelocityMessages
import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.util.*

class WebAccountLinkOtpConsumer(
    private val kafkaManager: KafkaManager,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
    proxyId: String,
) {
    private val gson = Gson()
    private val consumerRunner =
        ManagedKafkaStringConsumer(
            kafkaManager = kafkaManager,
            groupId = "ogcloud-velocity-web-link-otp-$proxyId",
            topic = KafkaTopics.WEB_ACCOUNT_LINK_OTP,
            threadName = "ogcloud-velocity-web-link-otp-consumer",
            logger = logger,
            consumerLabel = "web account link OTP",
            onRecord = ::processRecord,
        )

    fun start() {
        consumerRunner.start()
    }

    fun stop() {
        consumerRunner.stop()
    }

    private fun processRecord(payload: String) {
        val event = gson.fromJson(payload, WebAccountLinkOtpEvent::class.java)
        val playerUuid = parseUuid(event.playerUuid) ?: return
        val player = proxyServer.getPlayer(playerUuid).orElse(null) ?: return

        OgCloudCommand.sendMessage(
            player,
            VelocityMessages.format(
                VelocityMessages.Listener.WebLink.OTP,
                "prefix" to VelocityMessages.Prefix.WEB,
                "email" to event.requestedByEmail,
                "otp" to event.otp,
            ),
        )

        logger.info("Delivered web account link OTP to playerUuid={}", event.playerUuid)
    }

    private fun parseUuid(rawUuid: String): UUID? =
        runCatching { UUID.fromString(rawUuid) }
            .onFailure {
                logger.warn(
                    "Received web account link OTP with invalid uuid: {}",
                    rawUuid,
                )
            }.getOrNull()

    companion object {
    }
}
