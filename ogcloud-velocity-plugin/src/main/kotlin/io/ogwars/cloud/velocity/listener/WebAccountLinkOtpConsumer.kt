package io.ogwars.cloud.velocity.listener

import com.google.gson.Gson
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.api.event.WebAccountLinkOtpEvent
import io.ogwars.cloud.velocity.command.OgCloudCommand
import io.ogwars.cloud.velocity.kafka.KafkaManager
import org.slf4j.Logger
import java.util.*

class WebAccountLinkOtpConsumer(
    private val kafkaManager: KafkaManager,
    private val proxyServer: ProxyServer,
    private val logger: Logger,
    proxyId: String
) {

    private val gson = Gson()
    private val consumerRunner = ManagedKafkaStringConsumer(
        kafkaManager = kafkaManager,
        groupId = "ogcloud-velocity-web-link-otp-$proxyId",
        topic = TOPIC,
        threadName = "ogcloud-velocity-web-link-otp-consumer",
        logger = logger,
        consumerLabel = "web account link OTP",
        onRecord = ::processRecord
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
            player, "&8| &6OgCloud Web &7> &7Link code for &f${event.requestedByEmail}&7: &a${event.otp}"
        )

        logger.info("Delivered web account link OTP to playerUuid={}", event.playerUuid)
    }

    private fun parseUuid(rawUuid: String): UUID? {
        return runCatching { UUID.fromString(rawUuid) }.onFailure {
            logger.warn(
                "Received web account link OTP with invalid uuid: {}", rawUuid
            )
        }.getOrNull()
    }

    companion object {
        private const val TOPIC = "ogcloud.web.account.link.otp"
    }
}
