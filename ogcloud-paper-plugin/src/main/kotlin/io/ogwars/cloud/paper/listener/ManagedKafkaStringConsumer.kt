package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.paper.kafka.KafkaManager
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

internal class ManagedKafkaStringConsumer(
    private val kafkaManager: KafkaManager,
    private val groupId: String,
    private val topic: String,
    private val threadName: String,
    private val clientIdSuffix: String,
    private val autoOffsetReset: String,
    private val logger: Logger,
    private val consumerLabel: String,
    private val onRecord: (String) -> Unit
) {

    private val running = AtomicBoolean(false)
    private var consumerThread: Thread? = null

    @Volatile
    private var consumer: KafkaConsumer<String, String>? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        consumerThread = Thread(::runConsumerLoop, threadName).also { thread ->
            thread.isDaemon = true
            thread.start()
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }

        consumer?.wakeup()
        consumerThread?.join(THREAD_JOIN_TIMEOUT_MS)
        consumerThread = null
    }

    private fun runConsumerLoop() {
        val activeConsumer = kafkaManager.createConsumer(groupId, clientIdSuffix, autoOffsetReset)
        consumer = activeConsumer

        try {
            activeConsumer.subscribe(listOf(topic))

            while (running.get()) {
                val records = activeConsumer.poll(POLL_TIMEOUT)
                for (record in records) {
                    processRecord(record.value())
                }
            }
        } catch (_: WakeupException) {
            if (running.get()) {
                logger.severe("$consumerLabel consumer was interrupted unexpectedly")
            }
        } catch (exception: Exception) {
            logger.severe("$consumerLabel consumer thread crashed: ${exception.message}")
        } finally {
            consumer = null
            activeConsumer.close()
        }
    }

    private fun processRecord(payload: String) {
        try {
            onRecord(payload)
        } catch (exception: Exception) {
            logger.severe("Failed to process $consumerLabel event: ${exception.message}")
        }
    }

    companion object {
        private val POLL_TIMEOUT: Duration = Duration.ofMillis(500)
        private const val THREAD_JOIN_TIMEOUT_MS = 5000L
    }
}
