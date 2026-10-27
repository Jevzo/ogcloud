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

/** The open live-update streams of this replica; each event reaches only callers holding its permission. */
@Component
class LiveEvents {
    private val log = LoggerFactory.getLogger(javaClass)
    private val subscribers = CopyOnWriteArraySet<Subscriber>()

    private class Subscriber(
        val permissions: Set<String>,
        val emitter: SseEmitter,
    )

    /** Streams end after a while; the client reconnects with a new ticket. */
    fun subscribe(permissions: Set<String>): SseEmitter {
        val emitter = SseEmitter(STREAM_LIFETIME.toMillis())
        val subscriber = Subscriber(permissions, emitter)
        emitter.onCompletion { subscribers.remove(subscriber) }
        emitter.onTimeout { subscribers.remove(subscriber) }
        emitter.onError { subscribers.remove(subscriber) }
        subscribers += subscriber
        // Commits the response at once, so the client knows the stream is open before the first event.
        send(subscriber, SseEmitter.event().comment(OPENED))
        return emitter
    }

    fun publish(
        name: String,
        data: Any,
        permission: String,
    ) = subscribers
        .filter {
            permission in it.permissions
        }.forEach { send(it, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON)) }

    // Proxies close idle connections; a comment line keeps the stream open without waking the client.
    @Scheduled(fixedDelayString = "25s")
    fun keepAlive() = subscribers.forEach { send(it, SseEmitter.event().comment(KEEP_ALIVE)) }

    private fun send(
        subscriber: Subscriber,
        event: SseEmitter.SseEventBuilder,
    ) {
        try {
            subscriber.emitter.send(event)
        } catch (e: IOException) {
            log.debug("A live-update client went away", e)
            subscribers.remove(subscriber)
        } catch (_: IllegalStateException) {
            // The emitter completed meanwhile.
            subscribers.remove(subscriber)
        }
    }

    private companion object {
        const val OPENED = "opened"
        const val KEEP_ALIVE = "keep-alive"
        val STREAM_LIFETIME: Duration = Duration.ofMinutes(30)
    }
}
