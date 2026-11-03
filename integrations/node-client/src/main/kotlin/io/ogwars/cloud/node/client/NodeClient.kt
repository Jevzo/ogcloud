package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.DeliveryResult
import io.ogwars.cloud.contracts.node.v1.GatewayServiceGrpc
import io.ogwars.cloud.contracts.node.v1.Hello
import io.ogwars.cloud.contracts.node.v1.LiveChannelSubscription
import io.ogwars.cloud.contracts.node.v1.ProtocolVersion
import io.ogwars.cloud.contracts.node.v1.ServerCommandRequest
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.SessionResponse
import io.grpc.CallCredentials
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** How long to wait before reconnecting: [first], doubling up to [max]. */
data class Backoff(
    val first: Duration = Duration.ofSeconds(1),
    val max: Duration = Duration.ofSeconds(30),
)

/**
 * A node's connection to the gateway (D-0008): one session stream with Hello and Welcome, acknowledged deliveries,
 * server commands answered with results (D-0068), live channels, and the query stubs. It reconnects on its own and
 * renews its live subscriptions on every Welcome.
 */
class NodeClient(
    private val settings: NodeSettings,
    channel: ManagedChannel,
    credentials: CallCredentials,
    private val listener: SessionListener,
    private val scheduler: ScheduledExecutorService,
    private val requestIds: () -> String,
    private val backoff: Backoff = Backoff(),
) : AutoCloseable {
    private val log = System.getLogger(NodeClient::class.java.name)
    private val sessions = GatewayServiceGrpc.newStub(channel).withCallCredentials(credentials)

    /** Queries: servers, players, profiles, permission groups, NPCs, logins. */
    val queries: GatewayServiceGrpc.GatewayServiceFutureStub =
        GatewayServiceGrpc
            .newFutureStub(
                channel,
            ).withCallCredentials(credentials)

    private val lock = Any()
    private var stream: StreamObserver<SessionRequest>? = null
    private var welcomed = false
    private var failures = 0
    private var closed = false
    private val subscriptions = ConcurrentHashMap.newKeySet<String>()
    private val pending = ConcurrentHashMap<String, CompletableFuture<ServerCommandResult>>()

    fun start() = connect()

    /** Sends [request] if a session is open; false when it is not, e.g. while reconnecting. */
    fun send(request: SessionRequest): Boolean =
        synchronized(lock) {
            val open = stream.takeIf { welcomed } ?: return false
            open.onNext(request)
            true
        }

    /** Sends a server command and completes with its result, or fails when the session drops first. */
    fun serverCommand(command: ServerCommandRequest.Builder): CompletableFuture<ServerCommandResult> {
        val id = requestIds()
        val result = CompletableFuture<ServerCommandResult>()
        pending[id] = result
        if (!send(SessionRequest.newBuilder().setServerCommand(command.setRequestId(id)).build())) {
            pending.remove(id)
            result.completeExceptionally(NotConnectedException())
        }
        return result
    }

    fun subscribe(channel: String) {
        subscriptions += channel
        send(subscription(channel, true))
    }

    fun unsubscribe(channel: String) {
        subscriptions -= channel
        send(subscription(channel, false))
    }

    override fun close() {
        val open =
            synchronized(lock) {
                closed = true
                stream.also { stream = null }
            }
        open?.onCompleted()
        failPending(NotConnectedException())
    }

    private fun connect() {
        val requests =
            synchronized(lock) {
                if (closed) return
                welcomed = false
                val responses = Responses()
                // Under the lock, so a stream that fails at once still finds out which one it was.
                sessions.session(responses).also {
                    responses.requests = it
                    stream = it
                }
            }
        requests.onNext(SessionRequest.newBuilder().setHello(hello()).build())
    }

    private fun hello() =
        Hello
            .newBuilder()
            .setProtocolVersion(ProtocolVersion.PROTOCOL_VERSION_1)
            .setNodeType(settings.nodeType)
            .setIntegrationVersion(settings.integrationVersion)
            .addAllCapabilities(settings.capabilities)
            .build()

    private fun subscription(
        channel: String,
        subscribed: Boolean,
    ) = SessionRequest
        .newBuilder()
        .setLiveSubscription(LiveChannelSubscription.newBuilder().setChannel(channel).setSubscribed(subscribed))
        .build()

    private fun welcome(response: SessionResponse) {
        synchronized(lock) {
            welcomed = true
            failures = 0
        }
        subscriptions.forEach { send(subscription(it, true)) }
        listener.welcomed(response.welcome)
    }

    private fun deliver(delivery: Delivery) {
        listener
            .deliver(delivery)
            .exceptionally { error -> DeliveryOutcome(false, error.message ?: error.javaClass.simpleName) }
            .thenAccept { outcome ->
                val result =
                    DeliveryResult
                        .newBuilder()
                        .setDeliveryId(delivery.deliveryId)
                        .setSuccess(outcome.success)
                        .setMessage(outcome.message)
                send(SessionRequest.newBuilder().setDeliveryResult(result).build())
            }
    }

    private fun lost(
        requests: StreamObserver<SessionRequest>,
        cause: Throwable?,
    ) {
        val delay =
            synchronized(lock) {
                if (stream !== requests) return
                stream = null
                welcomed = false
                if (closed) return
                backoff.first.multipliedBy(1L shl minOf(failures++, MAX_DOUBLINGS)).coerceAtMost(backoff.max)
            }
        failPending(NotConnectedException(cause))
        listener.disconnected(cause)
        val reason = cause?.let { Status.fromThrowable(it).toString() } ?: "closed by the gateway"
        log.log(System.Logger.Level.WARNING, "Gateway session ended ({0}), reconnecting in {1}", reason, delay)
        scheduler.schedule(::connect, delay.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun failPending(cause: Exception) {
        pending.keys.toList().forEach { id -> pending.remove(id)?.completeExceptionally(cause) }
    }

    private inner class Responses : StreamObserver<SessionResponse> {
        lateinit var requests: StreamObserver<SessionRequest>

        override fun onNext(response: SessionResponse) {
            when (response.messageCase) {
                SessionResponse.MessageCase.WELCOME -> {
                    welcome(response)
                }

                SessionResponse.MessageCase.SERVER_LIFECYCLE -> {
                    listener.lifecycle(response.serverLifecycle)
                }

                SessionResponse.MessageCase.BROADCAST -> {
                    listener.broadcast(response.broadcast)
                }

                SessionResponse.MessageCase.DELIVERY -> {
                    deliver(response.delivery)
                }

                SessionResponse.MessageCase.LIVE_MESSAGE -> {
                    listener.liveMessage(response.liveMessage)
                }

                SessionResponse.MessageCase.SERVER_COMMAND_RESULT -> {
                    pending.remove(response.serverCommandResult.requestId)?.complete(response.serverCommandResult)
                }

                else -> {
                    log.log(System.Logger.Level.DEBUG, "Ignoring a session message of kind {0}", response.messageCase)
                }
            }
        }

        override fun onError(error: Throwable) = lost(synchronized(lock) { requests }, error)

        override fun onCompleted() = lost(synchronized(lock) { requests }, null)
    }

    private companion object {
        const val MAX_DOUBLINGS = 10
    }
}

/** The session is not open, e.g. while the client reconnects. */
class NotConnectedException(
    cause: Throwable? = null,
) : IllegalStateException("not connected to the gateway", cause)
