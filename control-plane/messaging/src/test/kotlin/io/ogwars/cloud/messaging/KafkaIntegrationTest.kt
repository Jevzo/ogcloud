package io.ogwars.cloud.messaging

import io.ogwars.cloud.contracts.events.v1.RequestServer
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.testcontainers.kafka.KafkaContainer
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Same broker line Strimzi 1.3 runs (Kafka 4.3). */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaIntegrationTest {
    private val kafka = KafkaContainer(KAFKA_IMAGE).also { it.start() }
    private val settings = KafkaSettings(kafka.bootstrapServers, clientId = "test")
    private val components = KafkaComponents(settings)
    private val template: KafkaTemplate<String, ByteArray> = components.template()
    private val publisher = EventPublisher(template)
    private val ids = AtomicLong()
    private val metadata =
        EventMetadataFactory(Clock.systemUTC(), IdGenerator { UUID(0, ids.incrementAndGet()) }, "test")
    private val fastRetry = RetryPolicy(initialInterval = Duration.ofMillis(10), maxAttempts = 3)
    private val groups = AtomicInteger()
    private val containers = mutableListOf<ConcurrentMessageListenerContainer<String, ByteArray>>()

    @AfterEach
    fun stopListeners() {
        containers.forEach { it.stop() }
        containers.clear()
    }

    @AfterAll
    fun stop() {
        template.destroy()
        kafka.stop()
    }

    @Test
    fun `events round-trip as Protobuf`() {
        val sent = request("lobby")
        val received = LinkedBlockingQueue<ServerCommand>()
        listen(KafkaTopics.SERVER_COMMAND, only = sent.serverId) { received.add(it.decode(ServerCommand.parser())) }

        publisher.publishAndWait(KafkaTopics.SERVER_COMMAND, sent.serverId, sent)

        received.poll(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe sent
    }

    @Test
    fun `failing records are retried, then parked in the dead-letter topic`() {
        val sent = request("bedwars")
        val attempts = AtomicInteger()
        listen(KafkaTopics.SERVER_COMMAND, attempts, only = sent.serverId) {
            throw IllegalStateException("controller unavailable")
        }

        publisher.publishAndWait(KafkaTopics.SERVER_COMMAND, sent.serverId, sent)

        val parked = readOne(KafkaTopics.SERVER_COMMAND_DLT, sent.serverId).shouldNotBeNull()
        ServerCommand.parseFrom(parked) shouldBe sent
        attempts.get() shouldBe fastRetry.maxAttempts
    }

    @Test
    fun `malformed records skip retries`() {
        val attempts = AtomicInteger()
        listen(KafkaTopics.SERVER_COMMAND, attempts, only = "garbage") { it.decode(ServerCommand.parser()) }

        template.send(KafkaTopics.SERVER_COMMAND, "garbage", byteArrayOf(0x0a, 0x7f, 0x01)).get()

        readOne(KafkaTopics.SERVER_COMMAND_DLT, "garbage").shouldNotBeNull()
        attempts.get() shouldBe 1
    }

    @Test
    fun `topics without a dead-letter topic skip non-retryable records and move on`() {
        val processed = LinkedBlockingQueue<String>()
        listen(KafkaTopics.SERVER_LIFECYCLE) {
            if (it.key() == "poison") throw NonRetryableEventException("cannot apply")
            processed.add(it.key())
        }

        template.send(KafkaTopics.SERVER_LIFECYCLE, "poison", byteArrayOf()).get()
        template.send(KafkaTopics.SERVER_LIFECYCLE, "healthy", byteArrayOf()).get()

        processed.poll(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe "healthy"
    }

    @Test
    fun `topics without a dead-letter topic keep retrying until the dependency recovers`() {
        val attempts = AtomicInteger()
        val failuresBeforeRecovery = fastRetry.maxAttempts + 2
        val processed = LinkedBlockingQueue<String>()
        listen(KafkaTopics.ALERT_RAISED, attempts, only = "outage") {
            if (attempts.get() <= failuresBeforeRecovery) throw IllegalStateException("valkey failing over")
            processed.add(it.key())
        }

        template.send(KafkaTopics.ALERT_RAISED, "outage", byteArrayOf()).get()

        processed.poll(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe "outage"
        attempts.get() shouldBe failuresBeforeRecovery + 1
    }

    private fun request(group: String): ServerCommand =
        ServerCommand
            .newBuilder()
            .setMetadata(metadata.create(Actor.system("test")))
            .setServerId(UUID.randomUUID().toString())
            .setRequest(RequestServer.newBuilder().setGroupId(group))
            .build()

    private fun listen(
        topic: String,
        attempts: AtomicInteger = AtomicInteger(),
        only: String? = null,
        handler: (org.apache.kafka.clients.consumer.ConsumerRecord<String, ByteArray>) -> Unit,
    ) {
        val factory =
            components.listenerContainerFactory(
                "ogcloud-test-${groups.incrementAndGet()}",
                template,
                fastRetry,
            )
        val container: ConcurrentMessageListenerContainer<String, ByteArray> = factory.createContainer(topic)
        container.setupMessageListener(
            MessageListener<String, ByteArray> {
                // Records of other tests share the topic; each test only handles its own key.
                if (only == null || it.key() == only) {
                    attempts.incrementAndGet()
                    handler(it)
                }
            },
        )
        containers.add(container)
        container.start()
    }

    /** Reads the first record with [key] from [topic], or null when none arrives in time. */
    private fun readOne(
        topic: String,
        key: String,
    ): ByteArray? {
        val properties =
            settings.commonProperties() +
                mapOf(
                    ConsumerConfig.GROUP_ID_CONFIG to "ogcloud-test-reader-${groups.incrementAndGet()}",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                )
        KafkaConsumer<String, ByteArray>(properties).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(POLL_MILLIS)).firstOrNull { it.key() == key }?.let { return it.value() }
            }
        }
        return null
    }

    private companion object {
        const val WAIT_SECONDS = 30L
        const val POLL_MILLIS = 250L
    }
}
