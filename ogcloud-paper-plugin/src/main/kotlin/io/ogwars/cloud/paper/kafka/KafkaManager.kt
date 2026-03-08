package io.ogwars.cloud.paper.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.*

class KafkaManager(
    private val bootstrapServers: String, private val serverId: String
) {

    private lateinit var producer: KafkaProducer<String, String>

    fun start() {
        producer = createWithPluginClassLoader { KafkaProducer(createProducerProperties()) }
    }

    fun createConsumer(
        groupId: String, clientIdSuffix: String, autoOffsetReset: String
    ): KafkaConsumer<String, String> {
        return createWithPluginClassLoader {
            KafkaConsumer(createConsumerProperties(groupId, clientIdSuffix, autoOffsetReset))
        }
    }

    fun close() {
        if (::producer.isInitialized) {
            producer.close(Duration.ofSeconds(PRODUCER_CLOSE_TIMEOUT_SECONDS))
        }
    }

    fun sendBlocking(topic: String, key: String, payload: String) {
        producer.send(ProducerRecord(topic, key, payload)).get()
    }

    private fun createProducerProperties(): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.CLIENT_ID_CONFIG, "ogcloud-paper-$serverId")
        }
    }

    private fun createConsumerProperties(
        groupId: String, clientIdSuffix: String, autoOffsetReset: String
    ): Properties {
        return Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset)
            put(ConsumerConfig.CLIENT_ID_CONFIG, "ogcloud-paper-$serverId-$clientIdSuffix")
        }
    }

    private fun <T> createWithPluginClassLoader(block: () -> T): T {
        val currentThread = Thread.currentThread()
        val originalClassLoader = currentThread.contextClassLoader

        currentThread.contextClassLoader = this::class.java.classLoader

        return try {
            block()
        } finally {
            currentThread.contextClassLoader = originalClassLoader
        }
    }

    companion object {
        private const val PRODUCER_CLOSE_TIMEOUT_SECONDS = 5L
    }
}
