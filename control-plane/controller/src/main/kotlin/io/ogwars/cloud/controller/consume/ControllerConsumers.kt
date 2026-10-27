package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.contracts.events.v1.GameStateChanged
import io.ogwars.cloud.contracts.events.v1.JobRequested
import io.ogwars.cloud.contracts.events.v1.NodeHeartbeat
import io.ogwars.cloud.contracts.events.v1.PlayerActivity
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.messaging.KafkaComponents
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.messaging.ListenerContainers
import io.ogwars.cloud.messaging.protobufContainer
import com.google.protobuf.Message
import com.google.protobuf.Parser
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * The controller's Kafka consumers. They run on every replica (D-0007), one consumer group per topic,
 * so partitions spread across replicas and each topic's lag is visible on its own.
 */
@Component
class ControllerConsumers(
    private val components: KafkaComponents,
    private val template: KafkaTemplate<String, ByteArray>,
    commands: ServerCommandHandler,
    heartbeats: HeartbeatHandler,
    gameStates: GameStateHandler,
    activity: PlayerActivityHandler,
    configChanges: ConfigChangedHandler,
    jobRequests: JobRequestedHandler,
) : ListenerContainers() {
    override val containers =
        listOf(
            container(
                KafkaTopics.SERVER_COMMAND,
                "commands",
                ServerCommand.parser(),
                concurrency = 1,
                commands::handle,
            ),
            container(
                KafkaTopics.NODE_HEARTBEAT,
                "heartbeats",
                NodeHeartbeat.parser(),
                BUSY_TOPIC_CONCURRENCY,
                heartbeats::handle,
            ),
            container(
                KafkaTopics.SERVER_GAMESTATE,
                "gamestates",
                GameStateChanged.parser(),
                concurrency = 1,
                gameStates::handle,
            ),
            container(
                KafkaTopics.PLAYER_ACTIVITY,
                "player-activity",
                PlayerActivity.parser(),
                BUSY_TOPIC_CONCURRENCY,
                activity::handle,
            ),
            container(
                KafkaTopics.CONFIG_CHANGED,
                "config",
                ConfigChanged.parser(),
                concurrency = 1,
                configChanges::handle,
            ),
            container(KafkaTopics.JOB_REQUESTED, "jobs", JobRequested.parser(), concurrency = 1, jobRequests::handle),
        )

    private fun <T : Message> container(
        topic: String,
        name: String,
        parser: Parser<T>,
        concurrency: Int,
        handle: (T) -> Unit,
    ) = components
        .listenerContainerFactory("$GROUP_PREFIX-$name", template, concurrency = concurrency)
        .protobufContainer(topic, parser, handle)

    private companion object {
        /** Matches the ACL prefix generated for the controller (ogcloud-controller). */
        const val GROUP_PREFIX = "ogcloud-controller"

        /** Heartbeats and player activity have 24 partitions and the most traffic. */
        const val BUSY_TOPIC_CONCURRENCY = 3
    }
}
