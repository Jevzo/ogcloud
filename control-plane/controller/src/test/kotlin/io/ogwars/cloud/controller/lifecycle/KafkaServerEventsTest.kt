package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.server
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import com.google.protobuf.Message
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CompletableFuture

class KafkaServerEventsTest {
    private val sent = slot<Message>()
    private val publisher =
        mockk<EventPublisher> {
            every { publish(KafkaTopics.SERVER_LIFECYCLE, any(), capture(sent)) } returns
                CompletableFuture.completedFuture(Unit)
        }
    private val events =
        KafkaServerEvents(publisher, EventMetadataFactory(Clock.systemUTC(), { UUID.randomUUID() }, "test"))

    @Test
    fun `lifecycle events carry what proxies register backends with`() {
        val running = server(state = ServerState.RUNNING, profileId = "pandaspigot-1.8.8")

        events.stateChanged(
            ServerChange(running.copy(state = ServerState.STARTING), running),
            Actor.system("test"),
            "heartbeat",
        )

        val event = sent.captured as ServerLifecycleChanged
        event.displayName shouldBe running.displayName
        event.protocol shouldBe RuntimeProfile.find("pandaspigot-1.8.8")?.protocol
    }
}
