package io.ogwars.cloud.controller.config

import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.api.event.PlayerTransferEvent
import io.ogwars.cloud.api.event.ServerLifecycleEvent
import com.fasterxml.jackson.core.JsonProcessingException
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.util.backoff.FixedBackOff
import java.util.concurrent.atomic.AtomicLong

@Configuration
class ControllerKafkaListenerConfig(
    private val kafkaProperties: KafkaProperties,
    private val consumerProperties: ControllerKafkaConsumerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val retryAttemptCounter = AtomicLong(0)
    private val retriesExhaustedCounter = AtomicLong(0)
    private val dltPublishCounter = AtomicLong(0)

    @Bean(name = ["busyKafkaListenerFactory"])
    fun busyKafkaListenerFactory(
        kafkaErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        buildFactory(
            tierName = "busy",
            tier = consumerProperties.busy,
            kafkaErrorHandler = kafkaErrorHandler,
        )

    @Bean(name = ["lightKafkaListenerFactory"])
    fun lightKafkaListenerFactory(
        kafkaErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        buildFactory(
            tierName = "light",
            tier = consumerProperties.light,
            kafkaErrorHandler = kafkaErrorHandler,
        )

    @Bean(name = ["singleKafkaListenerFactory"])
    fun singleKafkaListenerFactory(
        kafkaErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        buildFactory(
            tierName = "single",
            tier = consumerProperties.single,
            kafkaErrorHandler = kafkaErrorHandler,
        )

    @Bean
    fun deadLetterProducerFactory(): ProducerFactory<String, String> {
        val producerConfig = HashMap(kafkaProperties.buildProducerProperties(null))
        producerConfig[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        producerConfig[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return DefaultKafkaProducerFactory(producerConfig)
    }

    @Bean
    fun deadLetterKafkaTemplate(
        deadLetterProducerFactory: ProducerFactory<String, String>,
    ): KafkaTemplate<String, String> = KafkaTemplate(deadLetterProducerFactory)

    @Bean
    fun permissionUpdateKafkaTemplate(): KafkaTemplate<String, PermissionUpdateEvent> =
        KafkaTemplate(buildEventProducerFactory())

    @Bean
    fun serverLifecycleKafkaTemplate(): KafkaTemplate<String, ServerLifecycleEvent> =
        KafkaTemplate(buildEventProducerFactory())

    @Bean
    fun playerTransferKafkaTemplate(): KafkaTemplate<String, PlayerTransferEvent> =
        KafkaTemplate(buildEventProducerFactory())

    @Bean
    fun deadLetterPublishingRecoverer(
        deadLetterKafkaTemplate: KafkaTemplate<String, String>,
    ): DeadLetterPublishingRecoverer =
        DeadLetterPublishingRecoverer(deadLetterKafkaTemplate) { record, exception ->
            val dltTopic = "${record.topic()}$DLT_SUFFIX"
            val dltPublishCount = dltPublishCounter.incrementAndGet()
            log.error(
                "Routing failed kafka record to DLT: sourceTopic={}, dltTopic={}, partition={}, " +
                    "offset={}, key={}, dltPublishCount={}",
                record.topic(),
                dltTopic,
                record.partition(),
                record.offset(),
                record.key(),
                dltPublishCount,
                exception,
            )
            TopicPartition(dltTopic, record.partition())
        }

    @Bean
    fun kafkaErrorHandler(deadLetterPublishingRecoverer: DeadLetterPublishingRecoverer): DefaultErrorHandler {
        val backOff = FixedBackOff(consumerProperties.retry.backoffMs, consumerProperties.retry.maxRetries)
        val errorHandler = DefaultErrorHandler(deadLetterPublishingRecoverer, backOff)

        errorHandler.setCommitRecovered(true) // cannot use property access syntax (protected)
        errorHandler.addNotRetryableExceptions(
            JsonProcessingException::class.java,
            IllegalArgumentException::class.java,
        )
        errorHandler.setRetryListeners(
            object : RetryListener {
                override fun failedDelivery(
                    record: ConsumerRecord<*, *>,
                    ex: Exception,
                    deliveryAttempt: Int,
                ) {
                    val retryAttemptCount = retryAttemptCounter.incrementAndGet()
                    log.warn(
                        "Kafka listener retry attempt: topic={}, partition={}, offset={}, attempt={}, " +
                            "totalRetryAttempts={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        deliveryAttempt,
                        retryAttemptCount,
                        ex,
                    )
                }

                override fun recovered(
                    record: ConsumerRecord<*, *>,
                    ex: Exception,
                ) {
                    val retriesExhaustedCount = retriesExhaustedCounter.incrementAndGet()
                    log.error(
                        "Kafka listener retries exhausted and record recovered: topic={}, partition={}, " +
                            "offset={}, key={}, retriesExhaustedCount={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        retriesExhaustedCount,
                        ex,
                    )
                }

                override fun recoveryFailed(
                    record: ConsumerRecord<*, *>,
                    original: Exception,
                    failure: Exception,
                ) {
                    log.error(
                        "Kafka DLT recovery failed: topic={}, partition={}, offset={}, originalError={}, " +
                            "recoveryError={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        original.message,
                        failure.message,
                        failure,
                    )
                }
            },
        )

        return errorHandler
    }

    private fun buildFactory(
        tierName: String,
        tier: ControllerKafkaConsumerProperties.ConsumerTier,
        kafkaErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = buildConsumerFactory(tier.maxPollRecords)
        factory.setConcurrency(tier.concurrency)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        factory.containerProperties.pollTimeout = consumerProperties.pollTimeoutMs
        factory.setCommonErrorHandler(kafkaErrorHandler)

        log.info(
            "Configured kafka listener tier: tier={}, concurrency={}, maxPollRecords={}, pollTimeoutMs={}, " +
                "maxPollIntervalMs={}, sessionTimeoutMs={}, heartbeatIntervalMs={}, retryMaxRetries={}, " +
                "retryBackoffMs={}",
            tierName,
            tier.concurrency,
            tier.maxPollRecords,
            consumerProperties.pollTimeoutMs,
            consumerProperties.maxPollIntervalMs,
            consumerProperties.sessionTimeoutMs,
            consumerProperties.heartbeatIntervalMs,
            consumerProperties.retry.maxRetries,
            consumerProperties.retry.backoffMs,
        )

        return factory
    }

    private fun buildConsumerFactory(maxPollRecords: Int): ConsumerFactory<String, String> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties(null))
        consumerConfig[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = maxPollRecords
        consumerConfig[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = consumerProperties.maxPollIntervalMs
        consumerConfig[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = consumerProperties.sessionTimeoutMs
        consumerConfig[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = consumerProperties.heartbeatIntervalMs
        return DefaultKafkaConsumerFactory(consumerConfig)
    }

    private fun <T> buildEventProducerFactory(): ProducerFactory<String, T> {
        val producerConfig = HashMap(kafkaProperties.buildProducerProperties(null))
        return DefaultKafkaProducerFactory(producerConfig)
    }

    companion object {
        private const val DLT_SUFFIX = ".dlt"
    }
}
