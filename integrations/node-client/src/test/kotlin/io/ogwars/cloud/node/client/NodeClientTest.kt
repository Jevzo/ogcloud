package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.events.v1.KillServer
import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.node.v1.AdminRequest
import io.ogwars.cloud.contracts.node.v1.AdminRequestResult
import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.LiveChannelDelivery
import io.ogwars.cloud.contracts.node.v1.NetworkMaintenanceChange
import io.ogwars.cloud.contracts.node.v1.ServerCommandRequest
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.SessionResponse
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NodeClientTest {
    private val gateway = FakeGateway()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val welcomes = LinkedBlockingQueue<Welcome>()
    private var outcome: CompletableFuture<DeliveryOutcome> =
        CompletableFuture.completedFuture(
            DeliveryOutcome(true, "done"),
        )
    private val ids = AtomicInteger()
    private val tokenFile: Path = Files.createTempFile("token", "").also { Files.writeString(it, "pod-token\n") }

    private val listener =
        object : SessionListener {
            override fun welcomed(welcome: Welcome) {
                welcomes += welcome
            }

            override fun lifecycle(event: ServerLifecycleChanged) = Unit

            override fun broadcast(event: NodeBroadcast) = Unit

            override fun deliver(delivery: Delivery) = outcome

            override fun liveMessage(message: LiveChannelDelivery) = Unit

            override fun disconnected(cause: Throwable?) = Unit
        }

    private val client =
        NodeClient(
            NodeSettings("unused", tokenFile, Path.of("unused"), NodeType.NODE_TYPE_BACKEND, "2.0.0", listOf("npc")),
            gateway.channel,
            TokenCredentials(tokenFile, Clock.systemUTC()),
            listener,
            scheduler,
            { "r-${ids.incrementAndGet()}" },
            Backoff(Duration.ofMillis(10), Duration.ofMillis(50)),
        )

    @AfterEach
    fun close() {
        client.close()
        scheduler.shutdownNow()
        gateway.close()
    }

    private fun welcomed(): FakeGateway.Session {
        val session = gateway.nextSession()
        welcomes.poll(FakeGateway.WAIT_SECONDS, TimeUnit.SECONDS) ?: error("no welcome")
        return session
    }

    @Test
    fun `a session opens with Hello and the pod token`() {
        client.start()
        val session = welcomed()

        val hello = session.requests.first().hello
        hello.nodeType shouldBe NodeType.NODE_TYPE_BACKEND
        hello.integrationVersion shouldBe "2.0.0"
        hello.capabilitiesList shouldBe listOf("npc")
        session.authorization shouldBe "Bearer pod-token"
    }

    @Test
    fun `deliveries are answered with the listener's outcome`() {
        client.start()
        val session = welcomed()
        session.next()

        session.send(SessionResponse.newBuilder().setDelivery(Delivery.newBuilder().setDeliveryId("d-1")).build())
        val answered = session.next().deliveryResult
        outcome = CompletableFuture.failedFuture(IllegalStateException("no such player"))
        session.send(SessionResponse.newBuilder().setDelivery(Delivery.newBuilder().setDeliveryId("d-2")).build())
        val failed = session.next().deliveryResult

        (answered.deliveryId to answered.success) shouldBe ("d-1" to true)
        (failed.deliveryId to failed.success to failed.message) shouldBe ("d-2" to false to "no such player")
    }

    @Test
    fun `server commands complete with their result and fail when the session drops`() {
        client.start()
        val session = welcomed()
        session.next()

        val answered = client.serverCommand(ServerCommandRequest.newBuilder().setKill(KillServer.getDefaultInstance()))
        val request = session.next().serverCommand
        session.send(
            SessionResponse
                .newBuilder()
                .setServerCommandResult(
                    ServerCommandResult.newBuilder().setRequestId(request.requestId).setServerId("s-1"),
                ).build(),
        )
        val dropped = client.serverCommand(ServerCommandRequest.newBuilder().setKill(KillServer.getDefaultInstance()))
        session.next()
        session.responses.onError(Status.UNAVAILABLE.asRuntimeException())

        answered.get(FakeGateway.WAIT_SECONDS, TimeUnit.SECONDS).serverId shouldBe "s-1"
        shouldThrow<ExecutionException> {
            dropped.get(FakeGateway.WAIT_SECONDS, TimeUnit.SECONDS)
        }.cause.shouldBeInstanceOf<NotConnectedException>()
    }

    @Test
    fun `admin requests complete with the gateway's answer`() {
        client.start()
        val session = welcomed()
        session.next()

        val answered =
            client.adminRequest(
                AdminRequest
                    .newBuilder()
                    .setNetworkMaintenance(NetworkMaintenanceChange.newBuilder().setEnabled(true)),
            )
        val request = session.next().adminRequest
        session.send(
            SessionResponse
                .newBuilder()
                .setAdminRequestResult(
                    AdminRequestResult.newBuilder().setRequestId(request.requestId).setError("not_permitted"),
                ).build(),
        )

        answered.get(FakeGateway.WAIT_SECONDS, TimeUnit.SECONDS).error shouldBe "not_permitted"
    }

    @Test
    fun `a reconnected session renews its live subscriptions`() {
        client.start()
        val first = welcomed()
        client.subscribe("party")
        first.next()
        first.next().liveSubscription.channel shouldBe "party"

        first.responses.onError(Status.UNAVAILABLE.asRuntimeException())
        val second = welcomed()

        second.next().hasHello() shouldBe true
        second.next().liveSubscription.let { (it.channel to it.subscribed) shouldBe ("party" to true) }
    }

    @Test
    fun `nothing is sent before the gateway welcomed the node`() {
        client.send(
            SessionResponse.getDefaultInstance().let {
                io.ogwars.cloud.contracts.node.v1.SessionRequest
                    .getDefaultInstance()
            },
        ) shouldBe
            false
        shouldThrow<ExecutionException> {
            client
                .serverCommand(
                    ServerCommandRequest.newBuilder().setKill(KillServer.getDefaultInstance()),
                ).get(1, TimeUnit.SECONDS)
        }.cause.shouldBeInstanceOf<NotConnectedException>()
    }
}
