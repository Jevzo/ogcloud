package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.config.KafkaConfig
import io.ogwars.cloud.api.event.NetworkUpdateEvent
import io.ogwars.cloud.api.model.NetworkSettingsDocument
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class NetworkUpdateProducer(
    private val kafkaTemplate: KafkaTemplate<String, NetworkUpdateEvent>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishNetworkUpdate(settings: NetworkSettingsDocument) {
        log.info(
            "Publishing network update: maintenance={}, permissionSystemEnabled={}, tablistEnabled={}, proxyRoutingStrategy={}",
            settings.maintenance,
            settings.general.permissionSystemEnabled,
            settings.general.tablistEnabled,
            settings.general.proxyRoutingStrategy
        )

        kafkaTemplate.send(
            KafkaConfig.NETWORK_UPDATE, "global", NetworkUpdateEvent(
                motd = settings.motd,
                versionName = settings.versionName,
                maxPlayers = settings.maxPlayers,
                defaultGroup = settings.defaultGroup,
                maintenance = settings.maintenance,
                maintenanceKickMessage = settings.maintenanceKickMessage,
                tablist = settings.tablist,
                general = settings.general
            )
        )
    }
}
