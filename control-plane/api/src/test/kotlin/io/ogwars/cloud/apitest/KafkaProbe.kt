package io.ogwars.cloud.apitest

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.UUID

/** Looks for a record on a topic, reading it from the start; tests use fresh ids, so older records do not matter. */
class KafkaProbe(
    private val bootstrapServers: String,
) {
    fun received(
        topic: String,
        matches: (ByteArray) -> Boolean,
    ): Boolean =
        KafkaConsumer(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ),
            StringDeserializer(),
            ByteArrayDeserializer(),
        ).use { consumer ->
            consumer.subscribe(listOf(topic))
            (1..POLLS).any { _ -> consumer.poll(POLL).any { matches(it.value()) } }
        }

    private companion object {
        const val POLLS = 50
        val POLL: Duration = Duration.ofMillis(200)
    }
}
