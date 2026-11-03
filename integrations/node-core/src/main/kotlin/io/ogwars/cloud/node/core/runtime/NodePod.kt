package io.ogwars.cloud.node.core.runtime

import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.node.client.NodeSettings
import io.ogwars.cloud.node.core.text.Messages
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/** What every OgCloud plugin builds when it enables, from the environment the controller gave the pod. */
class NodePod(
    nodeType: NodeType,
    integrationVersion: String,
    val environment: Map<String, String> = System.getenv(),
) : AutoCloseable {
    val identity = NodeIdentity.fromEnvironment(environment)
    val messages = Messages.load()
    val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "ogcloud").apply { isDaemon = true } }
    val connection =
        GatewayConnection(NodeSettings.fromEnvironment(environment, nodeType, integrationVersion), scheduler)

    override fun close() {
        connection.close()
        scheduler.shutdown()
    }
}
