package io.ogwars.cloud.npc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class HubRuntimeNpcsTest {
    private val hub =
        NpcHub(RecordingPackets(), mockk(), "lobby", { _, _, _ -> }, mockk(relaxed = true), Clock.systemUTC())
    private val handedOver = mutableListOf<UUID>()
    private val npcs = HubRuntimeNpcs(hub) { player, task -> handedOver += player.also { task.run() } }

    @Test
    fun `runtime NPCs spawn, answer clicks on the clicking player's thread and despawn`() {
        val clicks = mutableListOf<Boolean>()
        val npc =
            npcs
                .create("guide")
                .location("world", 1.0, 64.0, 1.0, 0f, 0f)
                .title("&eGuide")
                .onClick { clicks += it.rightClick }
                .spawn()
        hub.shownIds() shouldBe setOf(NpcHub.RUNTIME + "guide")
        npcs.find("guide").isPresent shouldBe true

        val clicker = UUID.randomUUID()
        hub.clicked(clicker, 1_500_000_000, rightClick = true)
        npc.despawn()

        clicks shouldBe listOf(true)
        handedOver shouldBe listOf(clicker)
        hub.shownIds() shouldBe emptySet()
        npcs.find("guide").isPresent shouldBe false
    }

    @Test
    fun `a runtime NPC needs a location`() {
        shouldThrow<IllegalStateException> { npcs.create("lost").spawn() }
    }
}
