package io.ogwars.cloud.messaging

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.Parser
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.AuthenticationException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.ProducerListener
import org.springframework.util.backoff.ExponentialBackOff
import org.springframework.util.backoff.FixedBackOff
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** A record that can never succeed, so it skips retries and goes straight to the dead-letter topic. */
class NonRetryableEventException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Decodes a record value; malformed bytes are non-retryable by definition. */
fun <T : Message> ConsumerRecord<String, ByteArray>.decode(parser: Parser<T>): T =
    try {
        parser.parseFrom(value())
    } catch (e: InvalidProtocolBufferException) {
        throw NonRetryableEventException("Malformed record at ${topic()}-${partition()}@${offset()}", e)
    }

/**
 * Replaces the shared producer after a send fails in a way it does not recover from by itself: a producer whose first
 * sends ran before its SCRAM user existed keeps expiring every batch, while a fresh one works at once. An
 * authentication failure replaces it at once; timeouts only once nothing has gone through for [wedgedAfter], since a
 * busy broker times out single batches and a replacement would abort the others in flight. The reset runs off Kafka's
 * sender thread, which must not close its own producer, and only one is queued at a time.
 */
internal class ProducerRecovery(
    private val reset: () -> Unit,
    private val clock: Clock,
    private val wedgedAfter: Duration = KafkaComponents.DELIVERY_TIMEOUT,
    private val start: (Runnable) -> Unit = { Thread.ofVirtual().name("kafka-producer-reset").start(it) },
) : ProducerListener<String, ByteArray> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pending = AtomicBoolean(false)
    private val timingOutSince = AtomicReference<Instant?>(null)

    override fun onSuccess(
        record: ProducerRecord<String, ByteArray>,
        metadata: RecordMetadata,
    ) {
        timingOutSince.set(null)
    }

    override fun onError(
        record: ProducerRecord<String, ByteArray>,
        metadata: RecordMetadata?,
        exception: Exception,
    ) {
        if (!wedgesTheProducer(exception) || !pending.compareAndSet(false, true)) return
        timingOutSince.set(null)
        start {
            try {
                log.warn("Replacing the Kafka producer after: {}", exception.message)
                reset()
            } finally {
                pending.set(false)
            }
        }
    }

    private fun wedgesTheProducer(exception: Throwable): Boolean {
        val causes = generateSequence(exception) { it.cause }.toList()
        if (causes.any { it is AuthenticationException }) return true
        if (causes.none { it is TimeoutException }) return false
        val now = clock.instant()
        val since = timingOutSince.updateAndGet { it ?: now } ?: now
        return Duration.between(since, now) >= wedgedAfter
    }
}

data class RetryPolicy(
    val initialInterval: Duration = Duration.ofSeconds(1),
    val multiplier: Double = 2.0,
    /** Attempts before a record is dead-lettered; topics without a dead-letter topic retry until they succeed. */
    val maxAttempts: Int = 4,
    val maxInterval: Duration = Duration.ofSeconds(30),
)

private val failedAttemptLog = LoggerFactory.getLogger(KafkaComponents::class.java)

private fun logFailedAttempt(failure: FailedAttempt) {
    val record = failure.record
    failedAttemptLog.warn(
        "Delivery {} of {}-{}@{} failed; it will be retried",
        failure.attempt,
        record.topic(),
        record.partition(),
        record.offset(),
        failure.error,
    )
}

// Spring Kafka wraps what the listener threw.
private fun listenersOwn(error: Exception?): Exception? =
    if (error is ListenerExecutionFailedException) error.cause as? Exception ?: error else error

/** A delivery of a record that its listener failed; [attempt] counts from 1, and Spring Kafka may omit [error]. */
class FailedAttempt(
    val record: ConsumerRecord<*, *>,
    val error: Exception?,
    val attempt: Int,
)

/**
 * Builds producers and listener containers with OgCloud's delivery guarantees. [failed] hears every failed delivery
 * that will be retried, which for topics without a dead-letter topic may be forever; it logs them by default.
 */
class KafkaComponents(
    private val settings: KafkaSettings,
    private val failed: (FailedAttempt) -> Unit = ::logFailedAttempt,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun template(): KafkaTemplate<String, ByteArray> {
        val properties =
            settings.commonProperties() +
                mapOf(
                    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
                    ProducerConfig.ACKS_CONFIG to "all",
                    ProducerConfig.MAX_BLOCK_MS_CONFIG to MAX_BLOCK.toMillis().toInt(),
                    ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to REQUEST_TIMEOUT.toMillis().toInt(),
                    ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to DELIVERY_TIMEOUT.toMillis().toInt(),
                    ProducerConfig.LINGER_MS_CONFIG to LINGER.toMillis().toInt(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
                )
        val factory = DefaultKafkaProducerFactory<String, ByteArray>(properties)
        return KafkaTemplate(factory).apply { setProducerListener(ProducerRecovery(factory::reset, Clock.systemUTC())) }
    }

    /**
     * Listener containers for one consumer group. Failed records are retried with backoff and then parked in
     * `<topic>.dlt`. Topics without one retry until the dependency recovers, so an outage never drops records;
     * only non-retryable records are logged and skipped there.
     */
    fun listenerContainerFactory(
        groupId: String,
        template: KafkaTemplate<String, ByteArray>,
        retry: RetryPolicy = RetryPolicy(),
        concurrency: Int = 1,
    ): ConcurrentKafkaListenerContainerFactory<String, ByteArray> {
        requireGroupPrefix(groupId)
        val consumerProperties =
            settings.commonProperties() +
                mapOf(
                    ConsumerConfig.GROUP_ID_CONFIG to groupId,
                    // A dead replica's partitions move on well within the 30 s heartbeat deadline (D-0053).
                    ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to SESSION_TIMEOUT.toMillis().toInt(),
                    ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to HEARTBEAT_INTERVAL.toMillis().toInt(),
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                )
        return ConcurrentKafkaListenerContainerFactory<String, ByteArray>().apply {
            setConsumerFactory(DefaultKafkaConsumerFactory(consumerProperties))
            setConcurrency(concurrency)
            containerProperties.setAuthExceptionRetryInterval(AUTH_RETRY)
            setCommonErrorHandler(errorHandler(template, retry))
        }
    }

    /**
     * Listener containers that see every record from now on, e.g. one per API replica feeding its live-update
     * streams (D-0105). Offsets are never committed, so the group vanishes with its replica, and a record that
     * fails is logged and skipped: delivery is at-most-once.
     */
    fun broadcastContainerFactory(groupId: String): ConcurrentKafkaListenerContainerFactory<String, ByteArray> {
        requireGroupPrefix(groupId)
        val consumerProperties =
            settings.commonProperties() +
                mapOf(
                    ConsumerConfig.GROUP_ID_CONFIG to groupId,
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                )
        return ConcurrentKafkaListenerContainerFactory<String, ByteArray>().apply {
            setConsumerFactory(DefaultKafkaConsumerFactory(consumerProperties))
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            containerProperties.setAuthExceptionRetryInterval(AUTH_RETRY)
            setCommonErrorHandler(
                DefaultErrorHandler({ record, error ->
                    log.warn(
                        "Skipping broadcast record {}-{}@{}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        error,
                    )
                }, FixedBackOff(0, 0)),
            )
        }
    }

    private fun requireGroupPrefix(groupId: String) {
        require(groupId.startsWith("ogcloud-${settings.clientId}")) {
            "Group $groupId must start with ogcloud-${settings.clientId} (ACLs)"
        }
    }

    private fun errorHandler(
        template: KafkaTemplate<String, ByteArray>,
        retry: RetryPolicy,
    ): DefaultErrorHandler {
        val deadLetter =
            DeadLetterPublishingRecoverer(template) { record, _ ->
                TopicPartition(record.topic() + DLT_SUFFIX, record.partition())
            }
        val recoverer =
            ConsumerRecordRecoverer { record, error ->
                if (record.topic() in KafkaTopics.DEAD_LETTERED) {
                    deadLetter.accept(record, error)
                } else {
                    log.error(
                        "Dropping non-retryable record {}-{}@{}: no dead-letter topic",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        error,
                    )
                }
            }
        return DefaultErrorHandler(recoverer, backOff(retry, retry.maxAttempts)).apply {
            addNotRetryableExceptions(NonRetryableEventException::class.java)
            setRetryListeners(
                RetryListener { record, error, attempt -> failed(FailedAttempt(record, listenersOwn(error), attempt)) },
            )
            setBackOffFunction { record, _ ->
                if (record.topic() in KafkaTopics.DEAD_LETTERED) null else backOff(retry, attempts = null)
            }
        }
    }

    /** [attempts] counts the first delivery; null retries without limit. */
    private fun backOff(
        retry: RetryPolicy,
        attempts: Int?,
    ) = ExponentialBackOff(retry.initialInterval.toMillis(), retry.multiplier).apply {
        maxInterval = retry.maxInterval.toMillis()
        if (attempts != null) maxAttempts = (attempts - 1).toLong()
    }

    internal companion object {
        private const val DLT_SUFFIX = ".dlt"
        val MAX_BLOCK: Duration = Duration.ofSeconds(5)
        private val SESSION_TIMEOUT: Duration = Duration.ofSeconds(10)

        // A service may start before its KafkaUser exists (Strimzi creates it after the Secret syncs); a container that
        // fails to authenticate retries instead of stopping for good.
        private val AUTH_RETRY: Duration = Duration.ofSeconds(10)
        private val HEARTBEAT_INTERVAL: Duration = Duration.ofSeconds(3)

        // Kafka requires delivery >= linger + request timeout.
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(15)
        val DELIVERY_TIMEOUT: Duration = Duration.ofSeconds(30)
        private val LINGER: Duration = Duration.ofMillis(5)
    }
}
