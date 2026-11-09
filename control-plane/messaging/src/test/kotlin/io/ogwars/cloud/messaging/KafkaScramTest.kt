package io.ogwars.cloud.messaging

import io.ogwars.cloud.contracts.events.v1.KillServer
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.event.ConsumerRetryAuthEvent
import org.springframework.kafka.event.ConsumerStoppedEvent
import org.testcontainers.kafka.KafkaContainer
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class KafkaScramTest {
    @Test
    fun `publishes with SCRAM-SHA-512 credentials and rejects wrong ones`() {
        scramBroker().use { kafka ->
            kafka.start()
            createUser(kafka)

            publish(kafka, PASSWORD)
            shouldThrow<PublishException> { publish(kafka, "wrong") }
        }
    }

    @Test
    fun `a producer that first ran before its user existed publishes once the user is created`() {
        scramBroker().use { kafka ->
            kafka.start()
            val template = components(kafka, PASSWORD).template()
            try {
                shouldThrow<PublishException> { publish(template) }
                createUser(kafka)

                // The same producer, as a service's is after an install where it started before its KafkaUser.
                publishWithin(template, RECOVERY)
            } finally {
                template.destroy()
            }
        }
    }

    @Test
    fun `a listener that started before its user existed consumes once the user is created`() {
        scramBroker().use { kafka ->
            kafka.start()
            val components = components(kafka, PASSWORD)
            val template = components.template()
            val received = LinkedBlockingQueue<ServerCommand>()
            val events = LinkedBlockingQueue<ApplicationEvent>()
            val container =
                components
                    .listenerContainerFactory("ogcloud-test-commands", template)
                    .protobufContainer(KafkaTopics.SERVER_COMMAND, ServerCommand.parser()) { received.add(it) }
                    .apply { setApplicationEventPublisher { event -> (event as? ApplicationEvent)?.let(events::add) } }
            container.start()
            try {
                // Its first poll fails to authenticate before the user exists.
                generateSequence { events.poll(WAIT_SECONDS, TimeUnit.SECONDS) }
                    .first { it is ConsumerRetryAuthEvent || it is ConsumerStoppedEvent }
                createUser(kafka)

                publishWithin(template, RECOVERY)
                received.poll(RECOVERY.seconds, TimeUnit.SECONDS).shouldNotBeNull()
                container.isRunning shouldBe true
            } finally {
                container.stop()
                template.destroy()
            }
        }
    }

    // Created over the internal plaintext listener, as Strimzi's user operator would.
    private fun createUser(kafka: KafkaContainer) {
        val created =
            kafka.execInContainer(
                "/opt/kafka/bin/kafka-configs.sh",
                "--bootstrap-server",
                "localhost:9093",
                "--alter",
                "--add-config",
                "SCRAM-SHA-512=[password=$PASSWORD]",
                "--entity-type",
                "users",
                "--entity-name",
                USER,
            )
        created.exitCode shouldBe 0
    }

    private fun components(
        kafka: KafkaContainer,
        password: String,
    ) = KafkaComponents(KafkaSettings(kafka.bootstrapServers, "test", ScramCredentials(USER, password)))

    private fun publish(
        kafka: KafkaContainer,
        password: String,
    ) {
        val template = components(kafka, password).template()
        try {
            publish(template)
        } finally {
            template.destroy()
        }
    }

    private fun publish(template: KafkaTemplate<String, ByteArray>) {
        val event =
            ServerCommand
                .newBuilder()
                .setMetadata(
                    EventMetadataFactory(
                        Clock.systemUTC(),
                        IdGenerator { UUID(0, 1) },
                        "test",
                    ).create(Actor.system("test")),
                ).setServerId("s-1")
                .setKill(KillServer.newBuilder().setReason("test"))
                .build()
        EventPublisher(template).publishAndWait(KafkaTopics.SERVER_COMMAND, "s-1", event, Duration.ofSeconds(20))
    }

    // Credentials propagate to the broker a moment after they are written, so the first sends may still fail.
    private fun publishWithin(
        template: KafkaTemplate<String, ByteArray>,
        limit: Duration,
    ) {
        val deadline = System.nanoTime() + limit.toNanos()
        while (true) {
            try {
                return publish(template)
            } catch (e: PublishException) {
                if (System.nanoTime() > deadline) throw e
            }
        }
    }

    // The host-facing listener speaks SASL with SCRAM; the broker and controller listeners stay internal.
    private fun scramBroker(): KafkaContainer =
        KafkaContainer(KAFKA_IMAGE)
            .withEnv(
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                "BROKER:PLAINTEXT,PLAINTEXT:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT",
            ).withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "SCRAM-SHA-512")
            .withEnv(
                "KAFKA_LISTENER_NAME_PLAINTEXT_SCRAM___SHA___512_SASL_JAAS_CONFIG",
                "org.apache.kafka.common.security.scram.ScramLoginModule required;",
            )

    private companion object {
        const val USER = "ogcloud-test"
        const val PASSWORD = "s3cret-pass"
        const val WAIT_SECONDS = 30L
        val RECOVERY: Duration = Duration.ofSeconds(90)
    }
}
