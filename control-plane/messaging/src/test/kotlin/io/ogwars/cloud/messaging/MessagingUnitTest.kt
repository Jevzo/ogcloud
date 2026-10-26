package io.ogwars.cloud.messaging

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.common.IdGenerator
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class MessagingUnitTest {
    @Test
    fun `security protocol follows the configured SCRAM and TLS`() {
        val scram = ScramCredentials("ogcloud-api", "p\"w")
        val tls = TlsTrust("/etc/ogcloud/ca.crt")

        KafkaSettings("k:9092", "api").commonProperties() shouldContain ("security.protocol" to "PLAINTEXT")
        KafkaSettings("k:9092", "api", tls = tls).commonProperties() shouldContain ("security.protocol" to "SSL")
        KafkaSettings("k:9092", "api", scram = scram).commonProperties() shouldContain
            ("security.protocol" to "SASL_PLAINTEXT")

        val secure = KafkaSettings("k:9092", "api", scram, tls).commonProperties()
        secure shouldContain ("security.protocol" to "SASL_SSL")
        secure shouldContain ("sasl.mechanism" to "SCRAM-SHA-512")
        secure shouldContain ("ssl.truststore.type" to "PEM")
        secure["sasl.jaas.config"].toString() shouldContain "password=\"p\\\"w\""
    }

    @Test
    fun `metadata carries id, time, producer and actor`() {
        val at = Instant.parse("2026-10-26T12:00:00.5Z")
        val factory =
            EventMetadataFactory(Clock.fixed(at, ZoneOffset.UTC), IdGenerator { UUID(7, 7) }, producer = "api")

        val metadata = factory.create(Actor(ActorType.USER, "u-1"))

        metadata.eventId shouldBe UUID(7, 7).toString()
        metadata.occurredAt.seconds shouldBe at.epochSecond
        metadata.occurredAt.nanos shouldBe 500_000_000
        metadata.producer shouldBe "api"
        metadata.actor.userId shouldBe "u-1"
    }
}
