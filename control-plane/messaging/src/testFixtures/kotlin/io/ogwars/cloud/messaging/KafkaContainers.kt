package io.ogwars.cloud.messaging

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.kafka.KafkaContainer

/** Strimzi 1.3's newest Kafka, the broker version in the cluster (D-0021). */
const val KAFKA_IMAGE = "apache/kafka:4.3.1"

/** A plaintext broker wired into `ogcloud.kafka.*` for Spring tests of services. */
@TestConfiguration(proxyBeanMethods = false)
class KafkaContainerConfiguration {
    @Bean
    fun kafka(): KafkaContainer = KafkaContainer(KAFKA_IMAGE)

    @Bean
    fun kafkaProperties(kafka: KafkaContainer) =
        DynamicPropertyRegistrar { registry ->
            registry.add("ogcloud.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
}
