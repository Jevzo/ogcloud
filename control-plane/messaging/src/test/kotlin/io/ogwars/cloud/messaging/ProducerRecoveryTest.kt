package io.ogwars.cloud.messaging

import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SaslAuthenticationException
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ProducerRecoveryTest {
    private var now = Instant.parse("2026-11-10T12:00:00Z")
    private val clock =
        object : Clock() {
            override fun instant() = now

            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: ZoneId) = this
        }
    private var resets = 0
    private val queued = mutableListOf<Runnable>()
    private val recovery = ProducerRecovery({ resets++ }, clock, start = queued::add)
    private val record = ProducerRecord("ogcloud.test", "key", ByteArray(0))

    private fun failed(exception: Exception) = recovery.onError(record, null, exception)

    private fun runQueued() {
        queued.toList().forEach(Runnable::run)
        queued.clear()
    }

    @Test
    fun `an authentication failure replaces the producer at once`() {
        failed(KafkaException("send failed", SaslAuthenticationException("unknown user")))
        runQueued()

        resets shouldBe 1
    }

    // A busy or briefly unreachable broker times out single batches; replacing the producer would abort the others.
    @Test
    fun `timeouts replace the producer only once nothing went through for a while`() {
        failed(TimeoutException("batch expired"))
        now = now.plus(WEDGED.minusSeconds(1))
        failed(TimeoutException("batch expired"))
        runQueued()
        resets shouldBe 0

        now = now.plusSeconds(1)
        failed(TimeoutException("batch expired"))
        runQueued()
        resets shouldBe 1
    }

    @Test
    fun `a send that goes through starts the count again`() {
        failed(TimeoutException("batch expired"))
        now = now.plus(WEDGED)
        recovery.onSuccess(record, RecordMetadata(TopicPartition(record.topic(), 0), 0, 0, 0, 0, 0))
        failed(TimeoutException("batch expired"))
        runQueued()

        resets shouldBe 0
    }

    @Test
    fun `other failures leave the producer alone`() {
        failed(KafkaException("record too large"))
        now = now.plus(WEDGED.multipliedBy(2))
        failed(KafkaException("record too large"))
        runQueued()

        resets shouldBe 0
    }

    @Test
    fun `only one replacement is queued at a time`() {
        failed(SaslAuthenticationException("unknown user"))
        failed(SaslAuthenticationException("unknown user"))
        runQueued()
        failed(SaslAuthenticationException("unknown user"))
        runQueued()

        resets shouldBe 2
    }

    private companion object {
        val WEDGED: Duration = KafkaComponents.DELIVERY_TIMEOUT
    }
}
