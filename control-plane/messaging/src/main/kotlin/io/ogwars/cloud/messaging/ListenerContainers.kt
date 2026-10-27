package io.ogwars.cloud.messaging

import com.google.protobuf.Message
import com.google.protobuf.Parser
import org.springframework.context.SmartLifecycle
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.listener.MessageListenerContainer

/** A service's listener containers, started and stopped with its application context. */
abstract class ListenerContainers : SmartLifecycle {
    protected abstract val containers: List<MessageListenerContainer>

    override fun start() = containers.forEach { it.start() }

    override fun stop() = containers.forEach { it.stop() }

    override fun isRunning() = containers.any { it.isRunning }
}

/** A container for [topic] that decodes each record with [parser] and hands it to [handle]. */
fun <T : Message> ConcurrentKafkaListenerContainerFactory<String, ByteArray>.protobufContainer(
    topic: String,
    parser: Parser<T>,
    handle: (T) -> Unit,
): ConcurrentMessageListenerContainer<String, ByteArray> =
    createContainer(topic).apply {
        setupMessageListener(MessageListener<String, ByteArray> { handle(it.decode(parser)) })
    }
