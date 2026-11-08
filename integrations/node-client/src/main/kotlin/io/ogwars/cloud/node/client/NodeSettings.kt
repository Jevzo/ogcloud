package io.ogwars.cloud.node.client

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.contracts.events.v1.NodeType
import java.nio.file.Path

/** Where and as what a node connects to the gateway (D-0013, D-0041). */
data class NodeSettings(
    val gatewayAddress: String,
    val tokenFile: Path,
    val caFile: Path,
    val nodeType: NodeType,
    /** OgCloud's own version, e.g. 2.0.0. */
    val integrationVersion: String,
    val capabilities: List<String> = emptyList(),
) {
    companion object {
        fun fromEnvironment(
            environment: Map<String, String>,
            nodeType: NodeType,
            integrationVersion: String,
            capabilities: List<String> = emptyList(),
        ): NodeSettings {
            fun required(name: String) =
                environment[name]?.takeIf(String::isNotBlank) ?: throw IllegalStateException("$name is not set")
            return NodeSettings(
                required(PodEnv.GATEWAY_ADDRESS),
                Path.of(required(PodEnv.GATEWAY_TOKEN_FILE)),
                Path.of(required(PodEnv.GATEWAY_CA_FILE)),
                nodeType,
                integrationVersion,
                capabilities,
            )
        }
    }
}
