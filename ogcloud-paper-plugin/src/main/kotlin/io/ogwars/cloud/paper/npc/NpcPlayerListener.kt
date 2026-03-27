package io.ogwars.cloud.paper.npc

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class NpcPlayerListener(
    private val npcManager: NpcManager,
) : Listener {
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        npcManager.handleViewerQuit(event.player.uniqueId)
    }
}
