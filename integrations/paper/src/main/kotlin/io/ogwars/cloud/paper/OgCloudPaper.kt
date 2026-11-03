package io.ogwars.cloud.paper

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.node.core.command.CommandTree
import io.ogwars.cloud.node.core.runtime.NodePod
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.sdk.OgCloud
import io.ogwars.cloud.sdk.OgCloudInternalApi
import io.ogwars.cloud.sdk.RuntimeNpc
import io.ogwars.cloud.sdk.RuntimeNpcs
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import java.time.Clock
import java.util.Optional

/** OgCloud's Paper plugin: connects the server to the gateway (D-0008) and adapts node-core to Paper. */
class OgCloudPaper : JavaPlugin() {
    private var node: Node? = null

    override fun onEnable() {
        node = Node().also { it.start() }
    }

    override fun onDisable() {
        node?.close()
        node = null
    }

    /** Everything that lives while the server runs. */
    private inner class Node : AutoCloseable {
        private val pod = NodePod(NodeType.NODE_TYPE_BACKEND, pluginMeta.version)
        private val identity = pod.identity
        private val tablist = TeamTablist(server) { runtime.permissions.rank(it) }
        private val runtime: ServerRuntime =
            ServerRuntime(
                identity,
                PaperPlatform(this@OgCloudPaper, identity.serverId, tablist),
                pod.connection.queries,
                pod.messages,
                pod.scheduler,
                NoRuntimeNpcs,
                pod.connection::session,
            )

        @OptIn(OgCloudInternalApi::class)
        fun start() {
            server.maxPlayers = identity.maxPlayers
            val tokensFile =
                pod.environment[PodEnv.FORWARDING_TOKENS_FILE]
                    ?: throw IllegalStateException("${PodEnv.FORWARDING_TOKENS_FILE} is not set")
            val tokens = ForwardingTokens(Path.of(tokensFile), Clock.systemUTC())
            server.pluginManager.registerEvents(PaperListeners(runtime, tokens, tablist, logger), this@OgCloudPaper)
            registerCommand(COMMAND, listOf(ALIAS), PaperCommand(CommandTree(pod.messages, emptyList(), Message.INFO)))
            OgCloud.install(runtime.api)
            runtime.start()
        }

        @OptIn(OgCloudInternalApi::class)
        override fun close() {
            OgCloud.install(null)
            runtime.close()
            pod.close()
        }
    }

    /** TODO(plan: phase-05-integrations) T-0506 brings runtime NPCs on PacketEvents (D-0125). */
    private object NoRuntimeNpcs : RuntimeNpcs {
        override fun create(id: String): RuntimeNpc.Builder =
            throw UnsupportedOperationException("Runtime NPCs arrive with T-0506")

        override fun find(id: String): Optional<RuntimeNpc> = Optional.empty()
    }

    private companion object {
        const val COMMAND = "ogcloud"
        const val ALIAS = "oc"
    }
}
