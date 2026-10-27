package io.ogwars.cloud.api.events

import io.ogwars.cloud.domain.common.sha256Hex
import io.ogwars.cloud.domain.store.OneTimeKind
import io.ogwars.cloud.domain.store.OneTimeStore
import io.ogwars.cloud.service.SecretGenerator
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.LinkedBlockingQueue

data class EventTicket(
    val ticket: String,
    val expiresAt: Instant,
)

/** Single-use tickets that let an EventSource, which sends no headers, open the stream (D-0105). */
@Component
class EventTickets(
    private val store: OneTimeStore,
    private val secrets: SecretGenerator,
    private val clock: Clock,
) {
    fun issue(permissions: Set<String>): EventTicket {
        val ticket = secrets.next()
        store.put(OneTimeKind.SSE_TICKET, sha256Hex(ticket), permissions.joinToString(SEPARATOR), LIFETIME)
        return EventTicket(ticket, clock.instant().plus(LIFETIME))
    }

    /** The permissions the ticket was issued with, or null once used or expired. */
    fun redeem(ticket: String): Set<String>? =
        store
            .take(OneTimeKind.SSE_TICKET, sha256Hex(ticket))
            ?.split(SEPARATOR)
            ?.filter(String::isNotEmpty)
            ?.toSet()

    private companion object {
        const val SEPARATOR = " "
        val LIFETIME: Duration = Duration.ofSeconds(30)
    }
}

/**
 * The open live-update streams of this replica; each event reaches only callers holding its permission. Every
 * stream has its own queue drained on a virtual thread, so a slow or vanished client never holds up the Kafka
 * consumers or the other streams (D-0105).
 */
@Component
class LiveEvents {
    private val log = LoggerFactory.getLogger(javaClass)
    private val subscribers = CopyOnWriteArraySet<Subscriber>()

    private class Outgoing(
        val name: String,
        val event: SseEmitter.SseEventBuilder,
    )

    private inner class Subscriber(
        val permissions: Set<String>,
        val emitter: SseEmitter,
    ) {
        private val queue = LinkedBlockingQueue<Outgoing>(QUEUE_CAPACITY)
        private val sender = Thread.ofVirtual().name("live-events").unstarted(::drain)

        fun start() = sender.start()

        /** False when the client falls too far behind. */
        fun offer(outgoing: Outgoing): Boolean = queue.offer(outgoing)

        fun stop() {
            subscribers.remove(this)
            sender.interrupt()
        }

        private fun drain() {
            try {
                while (true) send(queue.take())
            } catch (_: InterruptedException) {
                // The stream ended.
            }
        }

        private fun send(outgoing: Outgoing) {
            try {
                emitter.send(outgoing.event)
            } catch (e: IOException) {
                log.debug("A live-update client went away", e)
                end(e)
            } catch (e: IllegalStateException) {
                // Without a cause the emitter had completed; with one, the event could not be written.
                if (e.cause != null) log.error("Could not send the live-update event {}", outgoing.name, e)
                end(e)
            }
        }

        /** Ends the stream with an error, so the client reconnects and refetches. */
        fun end(error: Exception) {
            stop()
            emitter.completeWithError(error)
        }
    }

    /** Streams end after a while; the client reconnects with a new ticket. */
    fun subscribe(permissions: Set<String>): SseEmitter {
        val emitter = SseEmitter(STREAM_LIFETIME.toMillis())
        val subscriber = Subscriber(permissions, emitter)
        emitter.onCompletion(subscriber::stop)
        emitter.onTimeout(subscriber::stop)
        emitter.onError { subscriber.stop() }
        // Commits the response at once, so the client knows the stream is open before the first event.
        subscriber.offer(Outgoing(OPENED, SseEmitter.event().comment(OPENED)))
        subscribers += subscriber
        subscriber.start()
        return emitter
    }

    fun publish(
        name: String,
        data: Any,
        permission: String,
    ) = subscribers
        .filter { permission in it.permissions }
        .forEach { enqueue(it, Outgoing(name, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON))) }

    // Proxies close idle connections; a comment line keeps the stream open without waking the client.
    @Scheduled(fixedDelayString = "25s")
    fun keepAlive() = subscribers.forEach { enqueue(it, Outgoing(KEEP_ALIVE, SseEmitter.event().comment(KEEP_ALIVE))) }

    private fun enqueue(
        subscriber: Subscriber,
        outgoing: Outgoing,
    ) {
        if (!subscriber.offer(outgoing)) {
            log.warn("Ending a live-update stream that fell {} events behind", QUEUE_CAPACITY)
            subscriber.end(IllegalStateException("The client fell too far behind"))
        }
    }

    private companion object {
        const val OPENED = "opened"
        const val KEEP_ALIVE = "keep-alive"
        const val QUEUE_CAPACITY = 1_000
        val STREAM_LIFETIME: Duration = Duration.ofMinutes(30)
    }
}
