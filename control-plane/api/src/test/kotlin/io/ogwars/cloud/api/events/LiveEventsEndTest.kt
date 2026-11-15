package io.ogwars.cloud.api.events

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** How a live-update stream ends when its client goes, falls behind or outlasts the stream's lifetime. */
class LiveEventsEndTest {
    private val events = LiveEvents()

    @Test
    fun `a client that went away is left to the container`() {
        val emitter = FakeEmitter(onSend = { throw IOException("Broken pipe") })
        events.subscribe(PERMISSIONS, emitter)

        emitter.sender.get(WAIT_SECONDS, TimeUnit.SECONDS).join(WAIT) shouldBe true
        events.publish(EVENT, DATA, PERMISSION)

        // Completing after a failed write dispatches into a request the container already failed, which throws.
        emitter.failure.isDone shouldBe false
        emitter.sends.get() shouldBe 1
    }

    @Test
    @Timeout(WAIT_SECONDS)
    fun `a client that fell behind is ended without holding up the publisher`() {
        val stalled = CountDownLatch(1)
        // A write to a stalled client holds the emitter's lock, so completing it waits for that write too.
        val emitter = FakeEmitter(onSend = stalled::await, onFailure = stalled::await)
        events.subscribe(PERMISSIONS, emitter)

        repeat(LiveEvents.QUEUE_CAPACITY + 1) { events.publish(EVENT, DATA, PERMISSION) }
        stalled.countDown()

        emitter.failure.get(WAIT_SECONDS, TimeUnit.SECONDS) shouldHaveMessage "The client fell too far behind"
    }

    @Test
    fun `a stream that outlasts its lifetime completes`() {
        val emitter = FakeEmitter()
        events.subscribe(PERMISSIONS, emitter)

        emitter.timeout.shouldNotBeNull().run()

        // An emitter left open at its timeout makes Spring answer with a timeout error on the committed response.
        emitter.completed.isDone shouldBe true
    }

    @Test
    fun `streams end when the API shuts down`() {
        val emitter = FakeEmitter()
        events.subscribe(PERMISSIONS, emitter)

        events.endStreams()

        // An open stream would hold the graceful shutdown until the pod is killed.
        emitter.completed.get(WAIT_SECONDS, TimeUnit.SECONDS)
        emitter.failure.isDone shouldBe false
    }

    /** An emitter whose writes behave as a real client's would, recording how the stream was ended. */
    private class FakeEmitter(
        private val onSend: () -> Unit = {},
        private val onFailure: () -> Unit = {},
    ) : SseEmitter() {
        val sender = CompletableFuture<Thread>()
        val sends = AtomicInteger()
        val failure = CompletableFuture<Throwable>()
        val completed = CompletableFuture<Unit>()

        @Volatile
        var timeout: Runnable? = null

        override fun send(builder: SseEventBuilder) {
            sender.complete(Thread.currentThread())
            sends.incrementAndGet()
            onSend()
        }

        override fun completeWithError(ex: Throwable) {
            onFailure()
            failure.complete(ex)
        }

        override fun complete() {
            completed.complete(Unit)
        }

        override fun onTimeout(callback: Runnable) {
            timeout = callback
        }
    }

    private companion object {
        const val PERMISSION = "servers:read"
        const val EVENT = "server"
        const val DATA = "{}"
        const val WAIT_SECONDS = 10L
        val PERMISSIONS = setOf(PERMISSION)
        val WAIT: Duration = Duration.ofSeconds(WAIT_SECONDS)
    }
}
