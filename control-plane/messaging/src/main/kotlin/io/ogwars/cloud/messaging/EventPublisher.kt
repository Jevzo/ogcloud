package io.ogwars.cloud.messaging

import com.google.protobuf.Message
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PublishException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

/** Publishes Protobuf events; every send result is observed, never fire-and-forget. */
class EventPublisher(
    private val kafka: KafkaTemplate<String, ByteArray>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Completes when the broker acknowledged the record; failures are logged with context and fail the future. */
    fun publish(
        topic: String,
        key: String,
        event: Message,
    ): CompletableFuture<Unit> {
        // Some failures (e.g. authentication) surface while sending, others when the broker answers.
        val sending =
            try {
                kafka.send(topic, key, event.toByteArray())
            } catch (e: RuntimeException) {
                return CompletableFuture.failedFuture(failure(topic, key, event, e))
            }
        return sending.handle { _, error -> if (error != null) throw failure(topic, key, event, error) }
    }

    private fun failure(
        topic: String,
        key: String,
        event: Message,
        error: Throwable,
    ): PublishException {
        log.error("Publishing {} with key {} to {} failed", event.descriptorForType.fullName, key, topic, error)
        return PublishException("Publishing to $topic failed", error)
    }

    /** For callers that must not continue until the event is stored, e.g. before answering an API request. */
    fun publishAndWait(
        topic: String,
        key: String,
        event: Message,
        timeout: Duration = DEFAULT_TIMEOUT,
    ) {
        try {
            publish(topic, key, event).get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw e.cause as? PublishException ?: PublishException("Publishing to $topic failed", e.cause ?: e)
        } catch (e: TimeoutException) {
            throw PublishException("Publishing to $topic timed out after $timeout", e)
        }
    }

    private companion object {
        // Waits out the producer's own delivery deadline; a shorter wait would report a failure for a record
        // that is still sent later, and a retrying caller would publish it twice.
        val DEFAULT_TIMEOUT: Duration =
            KafkaComponents.MAX_BLOCK + KafkaComponents.DELIVERY_TIMEOUT + Duration.ofSeconds(1)
    }
}
