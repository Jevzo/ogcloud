package io.ogwars.cloud.node.client

import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.TlsChannelCredentials

/** Opens the TLS channel to the gateway, trusting only the internal CA (D-0041). */
object GatewayChannels {
    fun open(settings: NodeSettings): ManagedChannel {
        val credentials =
            TlsChannelCredentials
                .newBuilder()
                .trustManager(settings.caFile.toFile())
                .build()
        return Grpc
            .newChannelBuilder(settings.gatewayAddress, credentials)
            .build()
    }
}
