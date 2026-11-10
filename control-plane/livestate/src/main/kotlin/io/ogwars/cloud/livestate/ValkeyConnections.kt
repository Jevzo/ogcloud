package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.common.redacted
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.SslOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.resource.ClientResources
import java.io.File
import java.time.Duration

data class HostAndPort(
    val host: String,
    val port: Int,
)

/** Where Valkey runs: standalone on minikube, Sentinel on Hetzner (D-0048). */
sealed interface ValkeyTopology {
    data class Standalone(
        val endpoint: HostAndPort,
    ) : ValkeyTopology

    data class Sentinel(
        val masterName: String,
        val sentinels: List<HostAndPort>,
    ) : ValkeyTopology {
        init {
            require(sentinels.isNotEmpty()) { "At least one sentinel is required" }
        }
    }
}

/** In-cluster TLS from the private CA (D-0041), as a PEM file mounted from a Secret. */
data class ValkeyTls(
    val caCertificatePath: String,
)

data class ValkeySettings(
    val topology: ValkeyTopology,
    /** ACL user; null uses the default user. */
    val username: String? = null,
    val password: String? = null,
    /** Null for plaintext. */
    val tls: ValkeyTls? = null,
    val timeout: Duration = Duration.ofSeconds(5),
) {
    override fun toString() =
        "ValkeySettings(topology=$topology, username=$username, password=${redacted(password)}, tls=$tls, " +
            "timeout=$timeout)"
}

/** String keys, Protobuf byte values. */
typealias ValkeyConnection = StatefulRedisConnection<String, ByteArray>

object ValkeyConnections {
    private val codec: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)

    /** Opens one thread-safe connection; the caller closes it and shuts the client down, which frees its resources. */
    fun connect(settings: ValkeySettings): Pair<RedisClient, ValkeyConnection> {
        val client = client(settings)
        return client to open(client)
    }

    /** One thread-safe connection with OgCloud's codec; closing it leaves [client] running. */
    fun open(client: RedisClient): ValkeyConnection = client.connect(codec)

    /** As [connect], with shared [resources] that the caller owns and shuts down itself. */
    fun connect(
        settings: ValkeySettings,
        resources: ClientResources,
    ): Pair<RedisClient, ValkeyConnection> {
        val client = client(settings, resources)
        return client to open(client)
    }

    /** A client for [settings]; the caller shuts it down, and shuts down [resources] if it passed them. */
    fun client(
        settings: ValkeySettings,
        resources: ClientResources? = null,
    ): RedisClient {
        val uri = uri(settings)
        val client = resources?.let { RedisClient.create(it, uri) } ?: RedisClient.create(uri)
        client.options = clientOptions(settings)
        return client
    }

    /** With TLS, trusts the private CA instead of the JVM's defaults. */
    fun clientOptions(settings: ValkeySettings): ClientOptions {
        val options = ClientOptions.builder()
        settings.tls?.let { options.sslOptions(SslOptions.builder().trustManager(File(it.caCertificatePath)).build()) }
        return options.build()
    }

    fun uri(settings: ValkeySettings): RedisURI {
        val builder =
            when (val topology = settings.topology) {
                is ValkeyTopology.Standalone -> {
                    RedisURI.Builder.redis(topology.endpoint.host, topology.endpoint.port)
                }

                is ValkeyTopology.Sentinel -> {
                    // Lettuce reaches each Sentinel with that Sentinel's own URI, so TLS and the user go on each one.
                    topology.sentinels.fold(RedisURI.builder().withSentinelMasterId(topology.masterName)) { b, s ->
                        b.withSentinel(secured(RedisURI.Builder.redis(s.host, s.port), settings).build())
                    }
                }
            }
        return secured(builder, settings).withTimeout(settings.timeout).build()
    }

    private fun secured(
        builder: RedisURI.Builder,
        settings: ValkeySettings,
    ): RedisURI.Builder {
        val password = settings.password?.toCharArray()
        val username = settings.username
        when {
            password != null && username != null -> builder.withAuthentication(username, password)
            password != null -> builder.withPassword(password)
        }
        return builder.withSsl(settings.tls != null)
    }
}
