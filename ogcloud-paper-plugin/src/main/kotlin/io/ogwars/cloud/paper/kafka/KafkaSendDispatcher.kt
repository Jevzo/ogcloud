package io.ogwars.cloud.paper.kafka

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

class KafkaSendDispatcher(
    private val kafkaManager: KafkaManager,
    private val logger: Logger,
    private val workerThreadName: String,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
) {
    enum class MessageType {
        SERVER_HEARTBEAT,
        GAME_STATE_UPDATE,
    }

    data class Message(
        val topic: String,
        val key: String,
        val payload: String,
        val type: MessageType,
    )

    private val queue = ArrayBlockingQueue<Message>(queueCapacity)
    private val running = AtomicBoolean(false)
    private lateinit var workerThread: Thread

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        workerThread =
            Thread(::runLoop, workerThreadName).apply {
                isDaemon = true
                start()
            }

        logger.info("Kafka send dispatcher started (thread=$workerThreadName, queueCapacity=$queueCapacity)")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }

        if (::workerThread.isInitialized) {
            workerThread.interrupt()
            try {
                workerThread.join(STOP_JOIN_TIMEOUT_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        logger.info("Kafka send dispatcher stopped (thread=$workerThreadName)")
    }

    fun dispatch(message: Message) {
        try {
            queue.put(message)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun runLoop() {
        while (running.get() || queue.isNotEmpty()) {
            val message = takeNextMessage() ?: continue
            send(message)
        }
    }

    private fun takeNextMessage(): Message? =
        try {
            if (running.get()) {
                queue.take()
            } else {
                queue.poll(STOP_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            null
        }

    private fun send(message: Message) {
        try {
            kafkaManager.sendBlocking(message.topic, message.key, message.payload)
        } catch (exception: Exception) {
            logger.log(
                Level.SEVERE,
                "Failed to dispatch Kafka message: type=${message.type}, topic=${message.topic}, key=${message.key}",
                exception,
            )
        }
    }

    companion object {
        private const val DEFAULT_QUEUE_CAPACITY = 2048
        private const val STOP_JOIN_TIMEOUT_MILLIS = 5000L
        private const val STOP_POLL_TIMEOUT_MILLIS = 100L
    }
}
