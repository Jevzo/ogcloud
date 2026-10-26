package io.ogwars.cloud.messaging

import io.ogwars.cloud.domain.common.IdGenerator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate
import java.time.Clock

/** The publisher side of Kafka (D-0074); services build listener factories per consumer group from [KafkaComponents]. */
@AutoConfiguration
@EnableConfigurationProperties(KafkaSettings::class)
class MessagingAutoConfiguration {
    @Bean
    fun kafkaComponents(settings: KafkaSettings): KafkaComponents = KafkaComponents(settings)

    @Bean
    fun kafkaTemplate(components: KafkaComponents): KafkaTemplate<String, ByteArray> = components.template()

    @Bean
    fun eventPublisher(template: KafkaTemplate<String, ByteArray>): EventPublisher = EventPublisher(template)

    @Bean
    fun eventMetadataFactory(
        settings: KafkaSettings,
        clock: Clock,
        ids: IdGenerator,
    ): EventMetadataFactory = EventMetadataFactory(clock, ids, settings.clientId)
}
