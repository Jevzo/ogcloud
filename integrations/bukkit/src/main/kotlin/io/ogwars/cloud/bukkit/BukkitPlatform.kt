package io.ogwars.cloud.bukkit

import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.node.core.runtime.HeartbeatSample
import io.ogwars.cloud.node.core.runtime.OnlinePlayer
import io.ogwars.cloud.node.core.runtime.ServerPlatform
import io.ogwars.cloud.npc.NpcHub
import org.bukkit.ChatColor
import org.bukkit.Server
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

/** node-core's view of a Paper or PandaSpigot server; [line] decides where game work runs. */
class BukkitPlatform(
    private val server: Server,
    private val serverId: String,
    private val tablist: TeamTablist,
    private val npcs: NpcHub,
    private val line: BukkitLine,
    private val ticks: TickSampler,
    private val logger: Logger,
) : ServerPlatform {
    override fun onlinePlayers(): List<OnlinePlayer> =
        server.onlinePlayers.map {
            OnlinePlayer(it.uniqueId, it.name, serverId)
        }

    override fun sample(): HeartbeatSample {
        val memory = Runtime.getRuntime()
        return HeartbeatSample(
            server.onlinePlayers.size,
            ticks.tps(),
            ticks.mspt(),
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
        online.sendMessage(legacy(text))
        return true
    }

    override fun runConsoleCommand(command: String): CompletableFuture<Boolean> {
        val known = CompletableFuture<Boolean>()
        line.global {
            runCatching {
                server.dispatchCommand(server.consoleSender, command)
            }.fold(known::complete, known::completeExceptionally)
        }
        return known
    }

    override fun ranksChanged(players: Set<UUID>) {
        players.mapNotNull(server::getPlayer).forEach { player ->
            line.player(player) {
                tablist.refresh(player)
                player.recalculatePermissions()
                line.refreshCommands(player)
            }
        }
    }

    /** Any change to the group's NPCs reloads them from the read model (D-0104). */
    override fun npcChanged(update: NpcUpdated) = reloadNpcs()

    override fun welcomed() = reloadNpcs()

    private fun reloadNpcs() {
        npcs.reload().whenComplete { _, error ->
            // The next NPC change or Welcome loads them again.
            if (error != null) logger.log(Level.WARNING, "Could not load the group's NPCs", error)
        }
    }

    override fun settingsChanged(settings: NetworkSettingsUpdated) =
        line.global {
            tablist.enable(settings.tablistEnabled)
        }

    companion object {
        /** node-core's `&` codes as Bukkit's `§` text, which every Bukkit version shows. */
        fun legacy(text: String): String = ChatColor.translateAlternateColorCodes('&', text)
    }
}
