package io.ogwars.cloud.bukkit

import io.ogwars.cloud.npc.NpcHub
import io.ogwars.cloud.npc.NpcLocation
import com.github.retrooper.packetevents.protocol.player.User
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class NpcTrackerTest {
    private val server = MockBukkit.mock()
    private val steve = server.addPlayer("Steve")
    private val world = steve.world
    private val hub = mockk<NpcHub>(relaxed = true)
    private val user = mockk<User>()
    private val tracker = NpcTracker(server, hub, InlineLine()) { if (it == steve) user else null }

    @AfterEach
    fun unmock() = MockBukkit.unmock()

    @Test
    fun `moves tell the hub where a known player stands, and quits remove them`() {
        val to = Location(world, 3.0, 64.0, 4.0, 90f, 0f)

        tracker.onMove(PlayerMoveEvent(steve, steve.location, to))
        tracker.onQuit(PlayerQuitEvent(steve, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED))

        verify {
            hub.moved(
                match {
                    it.uuid == steve.uniqueId &&
                        it.location == NpcLocation(world.name, 3.0, 64.0, 4.0, 90f, 0f) &&
                        it.user == user
                },
            )
            hub.left(steve.uniqueId)
        }
    }

    @Test
    fun `admins are placed and moved through the line`() {
        tracker.teleport(steve.uniqueId, NpcLocation(world.name, 10.0, 70.0, -2.0, 45f, 10f))

        tracker.location(steve.uniqueId) shouldBe NpcLocation(world.name, 10.0, 70.0, -2.0, 45f, 10f)
    }
}
