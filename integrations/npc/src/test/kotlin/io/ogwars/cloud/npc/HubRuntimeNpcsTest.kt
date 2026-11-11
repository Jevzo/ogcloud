package io.ogwars.cloud.npc

import io.ogwars.cloud.node.core.text.StoredTexts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

class HubRuntimeNpcsTest {
    private val hub =
        NpcHub(
            RecordingPackets(),
            mockk(),
            "lobby",
            { _, _, _ -> },
            mockk(relaxed = true),
            Clock.systemUTC(),
            StoredTexts(),
        )
    private val handedOver = mutableListOf<UUID>()
    private val npcs = HubRuntimeNpcs(hub) { player, task -> handedOver += player.also { task.run() } }

    @Test
    fun `runtime NPCs spawn, answer clicks on the clicking player's thread and despawn`() {
        val clicks = mutableListOf<Boolean>()
        val npc =
            npcs
                .create("guide")
                .location("world", 1.0, 64.0, 1.0, 0f, 0f)
                .title("<yellow>Guide")
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
    fun `an old handle cannot despawn the NPC that replaced it`() {
        val spawn = { title: String ->
            npcs
                .create("shop")
                .location("world", 0.0, 64.0, 0.0, 0f, 0f)
                .title(title)
                .spawn()
        }
        val old = spawn("Old")
        val current = spawn("New")

        old.despawn()

        hub.shownIds() shouldBe setOf(NpcHub.RUNTIME + "shop")
        current.despawn()
        hub.shownIds() shouldBe emptySet()
    }

    @Test
    fun `a runtime NPC needs a location`() {
        shouldThrow<IllegalStateException> { npcs.create("lost").spawn() }
    }

    @Test
    fun `an invalid title or subtitle is refused before the NPC spawns`() {
        val builder = npcs.create("bad").location("world", 0.0, 64.0, 0.0, 0f, 0f)

        shouldThrow<IllegalArgumentException> { builder.title("<frog>Shop").spawn() }.message shouldBe
            "Runtime NPC title \"<frog>Shop\": unknown tag <frog> (at character 0)"
        shouldThrow<IllegalArgumentException> { builder.title("ok").subtitle("§cRed").spawn() }
        hub.shownIds() shouldBe emptySet()
    }
}
