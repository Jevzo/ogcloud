package io.ogwars.cloud.bukkit

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.node.core.command.CommandTree
import io.ogwars.cloud.node.core.runtime.NodePod
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.npc.HubRuntimeNpcs
import io.ogwars.cloud.npc.NpcClicks
import io.ogwars.cloud.npc.NpcCommands
import io.ogwars.cloud.npc.NpcHub
import io.ogwars.cloud.npc.NpcPackets
import io.ogwars.cloud.sdk.OgCloud
import io.ogwars.cloud.sdk.OgCloudInternalApi
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerPriority
import org.bukkit.Server
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.nio.file.Path
import java.time.Clock
import java.util.logging.Logger

/**
 * Everything a Paper or PandaSpigot server runs while it is up, connected to the gateway (D-0008). The plugins bring
 * only their [BukkitLine], NPC packets and the listeners that differ (D-0138).
 */
class BackendNode(
    private val plugin: Plugin,
    private val line: BukkitLine,
    npcPackets: NpcPackets,
    integrationVersion: String,
) : AutoCloseable {
    private val server = plugin.server
    private val pod = NodePod(NodeType.NODE_TYPE_BACKEND, integrationVersion)
    val identity = pod.identity
    private val ticks = TickSampler()
    private val tablist = TeamTablist(server, line) { runtime.permissions.rank(it) }
    private val npcs =
        NpcHub(
            npcPackets,
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
    val runtime: ServerRuntime =
        ServerRuntime(
            identity,
            BukkitPlatform(server, identity.serverId, tablist, npcs, line, ticks, plugin.logger),
            pod.connection.queries,
            pod.messages,
            pod.scheduler,
            pod.connection.callbacks,
            HubRuntimeNpcs(npcs) { player, task -> server.getPlayer(player)?.let { line.player(it, task) } },
            pod.connection::session,
        )

    /** Registers the shared listeners, [extras] and `/ogcloud`, installs the SDK and connects. */
    @OptIn(OgCloudInternalApi::class)
    fun start(vararg extras: Listener) {
        val tokensFile =
            pod.environment[PodEnv.FORWARDING_TOKENS_FILE]
                ?: throw IllegalStateException("${PodEnv.FORWARDING_TOKENS_FILE} is not set")
        val tokens = ForwardingTokens(Path.of(tokensFile), Clock.systemUTC())
        val tracker = NpcTracker(server, npcs, line)
        (listOf(BackendListeners(runtime, tokens, tablist, plugin.logger), tracker, ticks) + extras)
            .forEach { server.pluginManager.registerEvents(it, plugin) }
        val npcCommands = NpcCommands(npcs, pod.messages, identity.groupId, tracker, runtime::requestNpcChange)
        val command = checkNotNull(server.getPluginCommand(COMMAND)) { "plugin.yml lacks /$COMMAND" }
        command.executor = BukkitCommand(CommandTree(pod.messages, npcCommands.subcommands, Message.INFO), line)
        OgCloud.install(runtime.api)
        runtime.start()
        npcs.start()
    }

    @OptIn(OgCloudInternalApi::class)
    override fun close() {
        OgCloud.install(null)
        PacketEvents.getAPI().eventManager.unregisterListener(npcClicks)
        runtime.close()
        pod.close()
    }

    private companion object {
        const val COMMAND = "ogcloud"
    }
}

/**
 * Runs a backend plugin's start; if it fails the server stops, because without OgCloud's handshake check it would
 * take any forwarded login (D-0065). The error goes on to Bukkit, which logs it with the plugin.
 */
fun enableOrStop(
    server: Server,
    logger: Logger,
    enable: () -> Unit,
) {
    try {
        enable()
    } catch (e: Throwable) {
        logger.severe("OgCloud could not start, so the server stops instead of taking unchecked logins")
        server.shutdown()
        throw e
    }
}
