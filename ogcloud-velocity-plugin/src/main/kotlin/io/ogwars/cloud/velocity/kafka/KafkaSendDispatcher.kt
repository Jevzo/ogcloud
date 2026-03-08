package io.ogwars.cloud.velocity.kafka

import org.slf4j.Logger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class KafkaSendDispatcher(
    private val kafkaManager: KafkaManager,
    private val logger: Logger,
    private val workerThreadName: String,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
) {

    enum class MessageType {
        PLAYER_CONNECT, PLAYER_DISCONNECT, PLAYER_SWITCH, PROXY_HEARTBEAT, PERMISSION_EXPIRY
    }

    data class Message(
        val topic: String, val key: String, val payload: String, val type: MessageType
    )

    enum class DispatchResult {
        SUCCESS, FAILED, TIMED_OUT, INTERRUPTED
    }

    private data class DispatchRequest(
        val message: Message, val completion: CompletableFuture<DispatchResult>?
    )

    private val queue = ArrayBlockingQueue<DispatchRequest>(queueCapacity)
    private val running = AtomicBoolean(false)
    private lateinit var workerThread: Thread

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        workerThread = Thread(::runLoop, workerThreadName).apply {
            isDaemon = true
            start()
        }

        logger.info("Kafka send dispatcher started (thread={}, queueCapacity={})", workerThreadName, queueCapacity)
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

        logger.info("Kafka send dispatcher stopped (thread={})", workerThreadName)
    }

    fun dispatch(message: Message) {
        enqueue(DispatchRequest(message = message, completion = null))
    }

    fun dispatchAndWait(message: Message, timeoutMillis: Long): DispatchResult {
        if (timeoutMillis <= 0) {
            return DispatchResult.TIMED_OUT
        }

        val completion = CompletableFuture<DispatchResult>()
        enqueue(DispatchRequest(message = message, completion = completion))

        return try {
            completion.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            DispatchResult.INTERRUPTED
        } catch (_: java.util.concurrent.TimeoutException) {
            DispatchResult.TIMED_OUT
        } catch (_: Exception) {
            DispatchResult.FAILED
        }
    }

    private fun enqueue(request: DispatchRequest) {
        try {
            queue.put(request)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            request.completion?.complete(DispatchResult.INTERRUPTED)
        }
    }

    private fun runLoop() {
        while (running.get() || queue.isNotEmpty()) {
            val request = takeNextRequest() ?: continue
            val result = send(request.message)
            request.completion?.complete(result)
        }
    }

    private fun takeNextRequest(): DispatchRequest? {
        return try {
            if (running.get()) {
                queue.take()
            } else {
                queue.poll(STOP_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            null
        }
    }

    private fun send(message: Message): DispatchResult {
        return try {
            kafkaManager.sendBlocking(message.topic, message.key, message.payload)
            DispatchResult.SUCCESS
        } catch (exception: Exception) {
            logger.error(
                "Failed to dispatch Kafka message: type={}, topic={}, key={}",
                message.type,
                message.topic,
                message.key,
                exception
            )
            DispatchResult.FAILED
        }
    }

    companion object {
        private const val DEFAULT_QUEUE_CAPACITY = 2048
        private const val STOP_JOIN_TIMEOUT_MILLIS = 5000L
        private const val STOP_POLL_TIMEOUT_MILLIS = 100L
    }
}
