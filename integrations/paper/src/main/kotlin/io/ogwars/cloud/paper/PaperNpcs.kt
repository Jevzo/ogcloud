package io.ogwars.cloud.paper

import io.ogwars.cloud.npc.NpcHub
import io.ogwars.cloud.npc.NpcLocation
import io.ogwars.cloud.npc.NpcPlaces
import io.ogwars.cloud.npc.NpcViewer
import com.github.retrooper.packetevents.PacketEvents
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID

/** Tells the NPC hub where players are (D-0136), and moves them for `/ogcloud npc`. */
internal class PaperNpcs(
    private val server: Server,
    private val hub: NpcHub,
) : Listener,
    NpcPlaces {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = show(event.player, event.player.location)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) = show(event.player, event.to)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) = show(event.player, event.player.location)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) = show(event.player, event.respawnLocation)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = hub.left(event.player.uniqueId)

    override fun location(player: UUID): NpcLocation? = server.getPlayer(player)?.location?.toNpcLocation()

    override fun teleport(
        player: UUID,
        location: NpcLocation,
    ) {
        val world = server.getWorld(location.world) ?: return
        server
            .getPlayer(
                player,
            )?.teleportAsync(Location(world, location.x, location.y, location.z, location.yaw, location.pitch))
    }

    private fun show(
        player: Player,
        at: Location,
    ) {
        // PacketEvents knows a player once their connection is set up; until then there is nobody to send to.
        val user = PacketEvents.getAPI().playerManager.getUser(player) ?: return
        hub.moved(NpcViewer(player.uniqueId, at.toNpcLocation(), user))
    }

    private fun Location.toNpcLocation() = NpcLocation(world.name, x, y, z, yaw, pitch)
}
