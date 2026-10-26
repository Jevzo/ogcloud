package io.ogwars.cloud.messaging

import io.ogwars.cloud.contracts.events.v1.KillServer
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.testcontainers.kafka.KafkaContainer
import java.time.Clock
import java.time.Duration
import java.util.UUID

class KafkaScramTest {
    @Test
    fun `publishes with SCRAM-SHA-512 credentials and rejects wrong ones`() {
        scramBroker().use { kafka ->
            kafka.start()
            // Created over the internal plaintext listener, as Strimzi's user operator would.
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

            publish(kafka, PASSWORD)
            shouldThrow<PublishException> { publish(kafka, "wrong") }
        }
    }

    private fun publish(
        kafka: KafkaContainer,
        password: String,
    ) {
        val template =
            KafkaComponents(
                KafkaSettings(kafka.bootstrapServers, "test", ScramCredentials(USER, password)),
            ).template()
        try {
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
        } finally {
            template.destroy()
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
    }
}
