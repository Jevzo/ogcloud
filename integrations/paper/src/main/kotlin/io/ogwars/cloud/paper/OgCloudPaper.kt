package io.ogwars.cloud.paper

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.node.core.command.CommandTree
import io.ogwars.cloud.node.core.runtime.NodePod
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.npc.HubRuntimeNpcs
import io.ogwars.cloud.npc.ModernNpcPackets
import io.ogwars.cloud.npc.NpcClicks
import io.ogwars.cloud.npc.NpcCommands
import io.ogwars.cloud.npc.NpcHub
import io.ogwars.cloud.sdk.OgCloud
import io.ogwars.cloud.sdk.OgCloudInternalApi
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerPriority
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import java.time.Clock

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
        private val npcs =
            NpcHub(
                ModernNpcPackets(::legacyText),
                pod.connection.queries,
                identity.groupId,
                { player, group, strategy -> runtime.api.transferPlayerToGroup(player, group, strategy) },
                pod.scheduler,
                Clock.systemUTC(),
            )
        private val npcClicks =
            PacketEvents.getAPI().eventManager.registerListener(
                NpcClicks(npcs),
                PacketListenerPriority.NORMAL,
            )
        private val runtime: ServerRuntime =
            ServerRuntime(
                identity,
                PaperPlatform(this@OgCloudPaper, identity.serverId, tablist, npcs),
                pod.connection.queries,
                pod.messages,
                pod.scheduler,
                HubRuntimeNpcs(npcs),
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
            val places = PaperNpcs(server, npcs)
            server.pluginManager.registerEvents(places, this@OgCloudPaper)
            val npcCommands = NpcCommands(npcs, pod.messages, identity.groupId, places, runtime::requestNpcChange)
            registerCommand(
                COMMAND,
                listOf(ALIAS),
                PaperCommand(CommandTree(pod.messages, npcCommands.subcommands, Message.INFO)),
            )
            OgCloud.install(runtime.api)
            runtime.start()
            npcs.start()
            runtime.api.onReady { npcs.reload() }
        }

        @OptIn(OgCloudInternalApi::class)
        override fun close() {
            OgCloud.install(null)
            PacketEvents.getAPI().eventManager.unregisterListener(npcClicks)
            runtime.close()
            pod.close()
        }
    }

    private companion object {
        const val COMMAND = "ogcloud"
        const val ALIAS = "oc"
    }
}
