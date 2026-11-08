package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.SessionKeepalive
import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.TlsChannelCredentials
import java.util.concurrent.TimeUnit

/** Opens the TLS channel to the gateway, trusting only the internal CA (D-0041). */
object GatewayChannels {
    fun open(settings: NodeSettings): ManagedChannel {
        val credentials =
            TlsChannelCredentials
                .newBuilder()
                .trustManager(settings.caFile.toFile())
                .build()
        // Pings find a dead gateway or link in seconds; without them a half-open session lasts until TCP gives up.
        return Grpc
            .newChannelBuilder(settings.gatewayAddress, credentials)
            .keepAliveTime(SessionKeepalive.PING_SECONDS.toLong(), TimeUnit.SECONDS)
            .keepAliveTimeout(SessionKeepalive.PONG_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build()
    }
}
