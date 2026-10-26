package io.ogwars.cloud.livestate

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** `ogcloud.valkey.*`: either `host` and `port`, or `sentinel` (D-0048). */
@ConfigurationProperties("ogcloud.valkey")
data class ValkeyProperties(
    val host: String? = null,
    val port: Int = DEFAULT_PORT,
    val sentinel: Sentinel? = null,
    val username: String? = null,
    val password: String? = null,
    val tls: Boolean = false,
    val timeout: Duration = Duration.ofSeconds(5),
) {
    init {
        require(
            (host == null) != (sentinel == null),
        ) { "Set exactly one of ogcloud.valkey.host and ogcloud.valkey.sentinel" }
    }

    /** [nodes] as `host:port`. */
    data class Sentinel(
        val master: String,
        val nodes: List<String>,
    )

    fun toSettings(): ValkeySettings {
        val topology =
            sentinel?.let { ValkeyTopology.Sentinel(it.master, it.nodes.map(::hostAndPort)) }
                ?: ValkeyTopology.Standalone(HostAndPort(host ?: error("unreachable: host checked in init"), port))
        return ValkeySettings(topology, username, password, tls, timeout)
    }

    private fun hostAndPort(node: String): HostAndPort {
        val separator = node.lastIndexOf(':')
        require(separator > 0) { "Sentinel node '$node' must be host:port" }
        return HostAndPort(node.substring(0, separator), node.substring(separator + 1).toInt())
    }

    private companion object {
        const val DEFAULT_PORT = 6379
    }
}
