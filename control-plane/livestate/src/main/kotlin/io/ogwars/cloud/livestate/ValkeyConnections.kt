package io.ogwars.cloud.livestate

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.resource.ClientResources
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

data class ValkeySettings(
    val topology: ValkeyTopology,
    /** ACL user; null uses the default user. */
    val username: String? = null,
    val password: String? = null,
    val tls: Boolean = false,
    val timeout: Duration = Duration.ofSeconds(5),
)

/** String keys, Protobuf byte values. */
typealias ValkeyConnection = StatefulRedisConnection<String, ByteArray>

object ValkeyConnections {
    private val codec: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)

    /** Opens one thread-safe connection; the caller owns the client and closes both. */
    fun connect(
        settings: ValkeySettings,
        resources: ClientResources = ClientResources.create(),
    ): Pair<RedisClient, ValkeyConnection> {
        val client = RedisClient.create(resources, uri(settings))
        return client to client.connect(codec)
    }

    fun uri(settings: ValkeySettings): RedisURI {
        val builder =
            when (val topology = settings.topology) {
                is ValkeyTopology.Standalone -> {
                    RedisURI.Builder.redis(topology.endpoint.host, topology.endpoint.port)
                }

                is ValkeyTopology.Sentinel -> {
                    topology.sentinels.fold(
                        RedisURI.Builder.sentinel(
                            topology.sentinels.first().host,
                            topology.sentinels.first().port,
                            topology.masterName,
                        ),
                    ) { b, s ->
                        b.withSentinel(s.host, s.port)
                    }
                }
            }
        val password = settings.password
        if (password != null) {
            val username = settings.username
            if (username !=
                null
            ) {
                builder.withAuthentication(username, password.toCharArray())
            } else {
                builder.withPassword(password.toCharArray())
            }
        }
        return builder.withSsl(settings.tls).withTimeout(settings.timeout).build()
    }
}
