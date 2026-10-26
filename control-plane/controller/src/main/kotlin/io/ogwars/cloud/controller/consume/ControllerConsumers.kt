package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.GameStateChanged
import io.ogwars.cloud.contracts.events.v1.NodeHeartbeat
import io.ogwars.cloud.contracts.events.v1.PlayerActivity
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.messaging.KafkaComponents
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.messaging.decode
import com.google.protobuf.Message
import com.google.protobuf.Parser
import org.springframework.context.SmartLifecycle
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
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
) : SmartLifecycle {
    private val containers =
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
        )

    override fun start() = containers.forEach { it.start() }

    override fun stop() = containers.forEach { it.stop() }

    override fun isRunning() = containers.any { it.isRunning }

    private fun <T : Message> container(
        topic: String,
        name: String,
        parser: Parser<T>,
        concurrency: Int,
        handle: (T) -> Unit,
    ): ConcurrentMessageListenerContainer<String, ByteArray> =
        components
            .listenerContainerFactory("$GROUP_PREFIX-$name", template, concurrency = concurrency)
            .createContainer(topic)
            .apply { setupMessageListener(MessageListener<String, ByteArray> { handle(it.decode(parser)) }) }

    private companion object {
        /** Matches the ACL prefix generated for the controller (ogcloud-controller). */
        const val GROUP_PREFIX = "ogcloud-controller"

        /** Heartbeats and player activity have 24 partitions and the most traffic. */
        const val BUSY_TOPIC_CONCURRENCY = 3
    }
}
