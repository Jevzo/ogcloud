package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.node.v1.GatewayServiceGrpc
import io.ogwars.cloud.node.client.GatewayChannels
import io.ogwars.cloud.node.client.NodeClient
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.node.client.NodeSettings
import io.ogwars.cloud.node.client.SessionListener
import io.ogwars.cloud.node.client.TokenCredentials
import io.ogwars.cloud.node.core.query.GrpcNodeQueries
import io.ogwars.cloud.node.core.query.NodeQueries
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The pod's link to the gateway: one TLS channel with the pod's token (D-0013, D-0041) for the session and the
 * queries. Adapters build a runtime from it, e.g. `ProxyRuntime(..., connection.queries, ..., connection::session)`.
 */
class GatewayConnection(
    private val settings: NodeSettings,
    private val scheduler: ScheduledExecutorService,
) : AutoCloseable {
    private val channel = GatewayChannels.open(settings)
    private val credentials = TokenCredentials(settings.tokenFile, Clock.systemUTC())

    // Query answers run on virtual threads, so a slow callback never holds up gRPC's own threads.
    private val callbacks: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    val queries: NodeQueries =
        GrpcNodeQueries(
            GatewayServiceGrpc.newFutureStub(channel).withCallCredentials(credentials),
            QUERY_DEADLINE,
            callbacks,
        )

    fun session(listener: SessionListener): NodeSession =
        NodeClient(settings, channel, credentials, listener, scheduler, { UUID.randomUUID().toString() })

    override fun close() {
        channel.shutdown()
        callbacks.shutdown()
        if (!channel.awaitTermination(SHUTDOWN.toMillis(), TimeUnit.MILLISECONDS)) channel.shutdownNow()
    }

    private companion object {
        val QUERY_DEADLINE: Duration = Duration.ofSeconds(5)
        val SHUTDOWN: Duration = Duration.ofSeconds(5)
    }
}
