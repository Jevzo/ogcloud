package io.ogwars.cloud.api.link

import io.ogwars.cloud.contracts.events.v1.PlayerActivity
import io.ogwars.cloud.messaging.KafkaComponents
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.messaging.ListenerContainers
import io.ogwars.cloud.messaging.protobufContainer
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** Reads player connects so linked usernames follow Minecraft name changes (D-0106); API replicas share the partitions. */
@Component
class UsernameSync(
    components: KafkaComponents,
    template: KafkaTemplate<String, ByteArray>,
    private val links: MinecraftLinks,
) : ListenerContainers() {
    override val containers =
        listOf(
            components
                .listenerContainerFactory(GROUP, template)
                .protobufContainer(KafkaTopics.PLAYER_ACTIVITY, PlayerActivity.parser(), ::handle),
        )

    private fun handle(activity: PlayerActivity) {
        if (activity.hasConnected()) {
            links.syncUsername(
                UUID.fromString(activity.playerUuid),
                activity.connected.playerName,
            )
        }
    }

    private companion object {
        const val GROUP = "ogcloud-api-username-sync"
    }
}
