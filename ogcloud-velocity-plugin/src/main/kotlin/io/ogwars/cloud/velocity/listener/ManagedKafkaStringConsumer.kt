package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.api.kafka.KafkaConsumerRecoverySettings
import io.ogwars.cloud.api.kafka.KafkaHeaderNames
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.api.kafka.NonRetryableKafkaRecordException
import io.ogwars.cloud.velocity.kafka.KafkaManager
import com.google.gson.JsonParseException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.Logger
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

internal class ManagedKafkaStringConsumer(
    private val kafkaManager: KafkaManager,
    private val groupId: String,
    private val topic: String,
    private val threadName: String,
    private val logger: Logger,
    private val consumerLabel: String,
    private val consumerRecoverySettings: KafkaConsumerRecoverySettings,
    private val onRecord: (String) -> CompletableFuture<Unit>,
) {
    private val running = AtomicBoolean(false)
    private val retryTopic = KafkaTopics.retryTopic(topic)
    private var supervisorThread: Thread? = null

    @Volatile
    private var consumer: KafkaConsumer<String, String>? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        supervisorThread =
            Thread(::runSupervisorLoop, threadName).also { thread ->
                thread.isDaemon = true
                thread.start()
            }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }

        consumer?.wakeup()
        supervisorThread?.interrupt()
        supervisorThread?.join(THREAD_JOIN_TIMEOUT_MS)
        supervisorThread = null
    }

    private fun runSupervisorLoop() {
        var nextRestartBackoffMs = consumerRecoverySettings.restartInitialBackoffMs

        while (running.get()) {
            val sessionResult = runConsumerSession()
            val failure = sessionResult.failure ?: break

            if (!running.get()) {
                break
            }

            if (sessionResult.healthySession) {
                nextRestartBackoffMs = consumerRecoverySettings.restartInitialBackoffMs
            }

            val restartDelayMs = applyJitter(nextRestartBackoffMs)
            logger.warn(
                "{} consumer supervisor restarting after failure: sourceTopic={}, retryTopic={}, delayMs={}",
                consumerLabel,
                topic,
                retryTopic,
                restartDelayMs,
                failure,
            )

            if (!sleepBeforeRestart(restartDelayMs)) {
                break
            }

            if (!sessionResult.healthySession) {
                nextRestartBackoffMs =
                    (nextRestartBackoffMs * 2).coerceAtMost(consumerRecoverySettings.restartMaxBackoffMs)
            }
        }
    }

    private fun runConsumerSession(): ConsumerSessionResult {
        val activeConsumer = kafkaManager.createConsumer(groupId, clientIdSuffix = threadName)
        consumer = activeConsumer
        var healthySession = false

        return try {
            activeConsumer.subscribe(listOf(topic, retryTopic))

            while (running.get()) {
                val records = activeConsumer.poll(POLL_TIMEOUT)
                healthySession = true

                for (record in records) {
                    processRecord(activeConsumer, record)
                }
            }

            ConsumerSessionResult(failure = null, healthySession = healthySession)
        } catch (exception: WakeupException) {
            if (running.get()) {
                ConsumerSessionResult(failure = exception, healthySession = healthySession)
            } else {
                ConsumerSessionResult(failure = null, healthySession = healthySession)
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            if (running.get()) {
                ConsumerSessionResult(failure = exception, healthySession = healthySession)
            } else {
                ConsumerSessionResult(failure = null, healthySession = healthySession)
            }
        } catch (exception: Exception) {
            ConsumerSessionResult(failure = exception, healthySession = healthySession)
        } finally {
            consumer = null
            runCatching { activeConsumer.close(CONSUMER_CLOSE_TIMEOUT) }
                .onFailure { closeFailure ->
                    logger.warn("{} consumer close failed", consumerLabel, closeFailure)
                }
        }
    }

    private fun processRecord(
        activeConsumer: KafkaConsumer<String, String>,
        record: ConsumerRecord<String, String>,
    ) {
        var currentRetryAttempt = 0

        try {
            currentRetryAttempt = readRetryAttempt(record)
            awaitHandlerCompletion(onRecord(record.value()))
            commitRecord(activeConsumer, record)
        } catch (exception: Exception) {
            val failure = unwrapFailure(exception)

            if (failure is InterruptedException && !running.get()) {
                throw failure
            }

            when {
                isNonRetryableFailure(failure) -> {
                    publishToDlt(record, failure)
                    commitRecord(activeConsumer, record)
                }

                currentRetryAttempt >= MAX_RETRY_ATTEMPTS -> {
                    logger.error(
                        "{} consumer retry budget exhausted: sourceTopic={}, currentTopic={}, partition={}, offset={}, totalAttempts={}",
                        consumerLabel,
                        topic,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        currentRetryAttempt + 1,
                        failure,
                    )
                    publishToDlt(record, failure)
                    commitRecord(activeConsumer, record)
                }

                else -> {
                    publishToRetryTopic(record, currentRetryAttempt + 1, failure)
                    commitRecord(activeConsumer, record)
                }
            }
        }
    }

    private fun publishToRetryTopic(
        record: ConsumerRecord<String, String>,
        nextRetryAttempt: Int,
        failure: Throwable,
    ) {
        val retryRecord =
            ProducerRecord(retryTopic, record.key(), record.value()).also { producerRecord ->
                copyHeaders(record, producerRecord, setOf(KafkaHeaderNames.RETRY_ATTEMPT))
                producerRecord.headers().add(
                    KafkaHeaderNames.RETRY_ATTEMPT,
                    nextRetryAttempt.toString().toByteArray(UTF_8),
                )
            }

        kafkaManager.sendBlocking(retryRecord)
        logger.warn(
            "{} consumer published record to retry topic: sourceTopic={}, retryTopic={}, currentTopic={}, partition={}, offset={}, nextRetryAttempt={}",
            consumerLabel,
            topic,
            retryTopic,
            record.topic(),
            record.partition(),
            record.offset(),
            nextRetryAttempt,
            failure,
        )
    }

    private fun publishToDlt(
        record: ConsumerRecord<String, String>,
        failure: Throwable,
    ) {
        val dltTopic = KafkaTopics.dltTopic(topic)
        val dltRecord =
            ProducerRecord(dltTopic, record.key(), record.value()).also { producerRecord ->
                copyHeaders(record, producerRecord, DLT_HEADER_NAMES)
                producerRecord.headers().add(KafkaHeaderNames.DLT_ORIGINAL_TOPIC, record.topic().toByteArray(UTF_8))
                producerRecord.headers().add(
                    KafkaHeaderNames.DLT_ORIGINAL_PARTITION,
                    record.partition().toString().toByteArray(UTF_8),
                )
                producerRecord.headers().add(
                    KafkaHeaderNames.DLT_ORIGINAL_OFFSET,
                    record.offset().toString().toByteArray(UTF_8),
                )
                producerRecord.headers().add(
                    KafkaHeaderNames.DLT_ORIGINAL_CONSUMER_GROUP,
                    groupId.toByteArray(UTF_8),
                )
                producerRecord.headers().add(
                    KafkaHeaderNames.DLT_EXCEPTION_FQCN,
                    failure::class.java.name.toByteArray(UTF_8),
                )
                producerRecord.headers().add(
                    KafkaHeaderNames.DLT_EXCEPTION_MESSAGE,
                    (failure.message ?: failure::class.java.simpleName).toByteArray(UTF_8),
                )
            }

        try {
            kafkaManager.sendBlocking(dltRecord)
            logger.error(
                "{} consumer published record to DLT: sourceTopic={}, dltTopic={}, currentTopic={}, partition={}, offset={}",
                consumerLabel,
                topic,
                dltTopic,
                record.topic(),
                record.partition(),
                record.offset(),
                failure,
            )
        } catch (publishFailure: Exception) {
            logger.error(
                "{} consumer failed to publish record to DLT: sourceTopic={}, dltTopic={}, currentTopic={}, partition={}, offset={}",
                consumerLabel,
                topic,
                dltTopic,
                record.topic(),
                record.partition(),
                record.offset(),
                publishFailure,
            )
            throw publishFailure
        }
    }

    private fun commitRecord(
        activeConsumer: KafkaConsumer<String, String>,
        record: ConsumerRecord<String, String>,
    ) {
        val offset =
            mapOf(
                TopicPartition(record.topic(), record.partition()) to OffsetAndMetadata(record.offset() + 1),
            )
        activeConsumer.commitSync(offset)
    }

    private fun readRetryAttempt(record: ConsumerRecord<String, String>): Int {
        val header = record.headers().lastHeader(KafkaHeaderNames.RETRY_ATTEMPT)

        if (header == null) {
            if (record.topic() == retryTopic) {
                throw NonRetryableKafkaRecordException(
                    "Retry topic record is missing ${KafkaHeaderNames.RETRY_ATTEMPT} header",
                )
            }
            return 0
        }

        val retryAttempt =
            header
                .value()
                .toString(UTF_8)
                .toIntOrNull()
                ?: throw NonRetryableKafkaRecordException(
                    "Retry attempt header is not a valid integer",
                )

        if (retryAttempt < 0) {
            throw NonRetryableKafkaRecordException("Retry attempt header must not be negative")
        }

        return retryAttempt
    }

    private fun awaitHandlerCompletion(completion: CompletableFuture<Unit>) {
        try {
            completion.get()
        } catch (exception: ExecutionException) {
            throw unwrapFailure(exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw exception
        }
    }

    private fun unwrapFailure(exception: Throwable): Throwable =
        when (exception) {
            is ExecutionException -> exception.cause?.let(::unwrapFailure) ?: exception
            else ->
                exception.cause?.let { cause ->
                    if (exception.javaClass.simpleName == "CompletionException") {
                        unwrapFailure(cause)
                    } else {
                        exception
                    }
                } ?: exception
        }

    private fun isNonRetryableFailure(failure: Throwable): Boolean =
        failure is NonRetryableKafkaRecordException ||
            failure is JsonParseException ||
            failure is IllegalArgumentException

    private fun copyHeaders(
        record: ConsumerRecord<String, String>,
        producerRecord: ProducerRecord<String, String>,
        excludedHeaderNames: Set<String>,
    ) {
        record.headers().forEach { header ->
            if (header.key() !in excludedHeaderNames) {
                producerRecord.headers().add(header.key(), header.value())
            }
        }
    }

    private fun applyJitter(baseDelayMs: Long): Long {
        if (consumerRecoverySettings.restartJitterMs == 0L) {
            return baseDelayMs
        }

        val jitter = ThreadLocalRandom.current().nextLong(consumerRecoverySettings.restartJitterMs + 1)
        return (baseDelayMs + jitter).coerceAtMost(consumerRecoverySettings.restartMaxBackoffMs)
    }

    private fun sleepBeforeRestart(delayMs: Long): Boolean =
        try {
            Thread.sleep(delayMs)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    private data class ConsumerSessionResult(
        val failure: Throwable?,
        val healthySession: Boolean,
    )

    companion object {
        private val POLL_TIMEOUT: Duration = Duration.ofMillis(500)
        private val CONSUMER_CLOSE_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val DLT_HEADER_NAMES =
            setOf(
                KafkaHeaderNames.DLT_ORIGINAL_TOPIC,
                KafkaHeaderNames.DLT_ORIGINAL_PARTITION,
                KafkaHeaderNames.DLT_ORIGINAL_OFFSET,
                KafkaHeaderNames.DLT_ORIGINAL_CONSUMER_GROUP,
                KafkaHeaderNames.DLT_EXCEPTION_FQCN,
                KafkaHeaderNames.DLT_EXCEPTION_MESSAGE,
            )
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val THREAD_JOIN_TIMEOUT_MS = 5000L
    }
}
