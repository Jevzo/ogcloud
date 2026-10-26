package io.ogwars.cloud.messaging

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.Parser
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff
import java.time.Duration

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

data class RetryPolicy(
    val initialInterval: Duration = Duration.ofSeconds(1),
    val multiplier: Double = 2.0,
    val maxAttempts: Int = 4,
)

/** Builds producers and listener containers with OgCloud's delivery guarantees. */
class KafkaComponents(
    private val settings: KafkaSettings,
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
        return KafkaTemplate(DefaultKafkaProducerFactory(properties))
    }

    /**
     * Listener containers for one consumer group. Failed records are retried with backoff, then parked in
     * `<topic>.dlt` when the topic has one, or logged and skipped when it does not.
     */
    fun listenerContainerFactory(
        groupId: String,
        template: KafkaTemplate<String, ByteArray>,
        retry: RetryPolicy = RetryPolicy(),
        concurrency: Int = 1,
    ): ConcurrentKafkaListenerContainerFactory<String, ByteArray> {
        require(groupId.startsWith("ogcloud-${settings.clientId}")) {
            "Group $groupId must start with ogcloud-${settings.clientId} (ACLs)"
        }
        val consumerProperties =
            settings.commonProperties() +
                mapOf(
                    ConsumerConfig.GROUP_ID_CONFIG to groupId,
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                )
        return ConcurrentKafkaListenerContainerFactory<String, ByteArray>().apply {
            setConsumerFactory(DefaultKafkaConsumerFactory(consumerProperties))
            setConcurrency(concurrency)
            setCommonErrorHandler(errorHandler(template, retry))
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
                        "Dropping record {}-{}@{} after retries: no dead-letter topic",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        error,
                    )
                }
            }
        val backOff =
            ExponentialBackOff(retry.initialInterval.toMillis(), retry.multiplier).apply {
                // The first delivery is not a retry.
                maxAttempts = (retry.maxAttempts - 1).toLong()
            }
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(NonRetryableEventException::class.java)
        }
    }

    private companion object {
        const val DLT_SUFFIX = ".dlt"
        val MAX_BLOCK: Duration = Duration.ofSeconds(5)

        // Kafka requires delivery >= linger + request timeout.
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(15)
        val DELIVERY_TIMEOUT: Duration = Duration.ofSeconds(30)
        val LINGER: Duration = Duration.ofMillis(5)
    }
}
