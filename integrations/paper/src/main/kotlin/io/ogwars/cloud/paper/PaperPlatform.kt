package io.ogwars.cloud.paper

import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.node.core.runtime.HeartbeatSample
import io.ogwars.cloud.node.core.runtime.OnlinePlayer
import io.ogwars.cloud.node.core.runtime.ServerPlatform
import io.ogwars.cloud.npc.NpcHub
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * node-core's view of Paper. Work on the game runs through Paper's region-aware schedulers, never the Bukkit
 * scheduler, so the plugin also runs on Folia (D-0135).
 */
internal class PaperPlatform(
    private val plugin: Plugin,
    private val serverId: String,
    private val tablist: TeamTablist,
    private val npcs: NpcHub,
) : ServerPlatform {
    private val server get() = plugin.server

    override fun onlinePlayers(): List<OnlinePlayer> =
        server.onlinePlayers.map {
            OnlinePlayer(it.uniqueId, it.name, serverId)
        }

    override fun sample(): HeartbeatSample {
        val memory = Runtime.getRuntime()
        return HeartbeatSample(
            server.onlinePlayers.size,
            server.tps.first(),
            server.averageTickTime,
            memory.totalMemory() - memory.freeMemory(),
            memory.maxMemory(),
        )
    }

    override fun hasPermission(
        player: UUID,
        node: String,
    ): Boolean = server.getPlayer(player)?.hasPermission(node) ?: false

    override fun message(
        player: UUID,
        text: String,
    ): Boolean {
        val online = server.getPlayer(player) ?: return false
        online.sendMessage(legacyText(text))
        return true
    }

    override fun runConsoleCommand(command: String): CompletableFuture<Boolean> {
        val known = CompletableFuture<Boolean>()
        server.globalRegionScheduler.execute(plugin) {
            runCatching {
                server.dispatchCommand(server.consoleSender, command)
            }.fold(known::complete, known::completeExceptionally)
        }
        return known
    }

    override fun ranksChanged(players: Set<UUID>) {
        players.mapNotNull(server::getPlayer).forEach { player ->
            player.scheduler.run(plugin, {
                tablist.refresh(player)
                player.recalculatePermissions()
                player.updateCommands()
            }, null)
        }
    }

    /** Any change to the group's NPCs reloads them from the read model (D-0104). */
    override fun npcChanged(update: NpcUpdated) {
        npcs.reload()
    }

    override fun settingsChanged(settings: NetworkSettingsUpdated) {
        server.globalRegionScheduler.execute(plugin) { tablist.enable(settings.tablistEnabled) }
    }
}
