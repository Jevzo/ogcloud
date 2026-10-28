package io.ogwars.cloud.messaging

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.kafka.KafkaContainer

/** Pinned in testing/images.properties (D-0113). */
val KAFKA_IMAGE: String = System.getProperty("ogcloud.image.kafka")

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
