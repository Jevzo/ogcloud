package io.ogwars.cloud.npc

import io.ogwars.cloud.contracts.events.v1.RoutingStrategy
import io.ogwars.cloud.contracts.node.v1.Location
import io.ogwars.cloud.contracts.node.v1.NpcChangeRequest
import io.ogwars.cloud.node.core.command.CommandSender
import io.ogwars.cloud.node.core.command.CommandTree
import io.ogwars.cloud.node.core.command.prefixed
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.StoredTexts
import io.ogwars.cloud.text.RichText
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CompletableFuture.completedFuture
import io.ogwars.cloud.contracts.node.v1.Npc as ManagedNpc

class NpcCommandsTest {
    private val messages = Messages.load()
    private val shop =
        ManagedNpc
            .newBuilder()
            .setNpcId("0192d2c1-0000-7000-8000-4a1b2c3d4e5f")
            .setGroupId("lobby")
            .setLocation(Location.newBuilder().setWorld("world").setY(64.0))
            .setTitle("<green>Shop")
            .build()

    // Created in the same minute, so both ids start with the same timestamp.
    private val guide =
        shop
            .toBuilder()
            .setNpcId("0192d2c1-0003-7000-8000-9f8e7d6c5b4a")
            .setTitle("<yellow>Guide")
            .build()
    private val portal =
        shop
            .toBuilder()
            .setNpcId("0192d2c1-0004-7000-8000-000011112222")
            .setLocation(Location.newBuilder().setWorld("void"))
            .build()
    private val queries =
        mockk<NodeQueries> { every { npcs("lobby") } returns completedFuture(listOf(shop, guide, portal)) }
    private val hub =
        NpcHub(
            RecordingPackets(),
            queries,
            "lobby",
            { _, _, _ -> },
            mockk(relaxed = true),
            Clock.systemUTC(),
            StoredTexts(),
        )
    private val here = NpcLocation("world", 10.5, 70.0, -3.0, 90f, 0f)
    private val teleports = mutableListOf<NpcLocation>()
    private val sent = mutableListOf<NpcChangeRequest>()
    private val commands =
        NpcCommands(
            hub,
            messages,
            StoredTexts(),
            "lobby",
            object : NpcPlaces {
                override fun location(player: UUID) = here

                override fun teleport(
                    player: UUID,
                    location: NpcLocation,
                ) = (location.world == "world").also { if (it) teleports += location }
            },
        ) { sent.add(it.build()) }
    private val tree = CommandTree(messages, commands.subcommands, Message.INFO)
    private val admin = Admin(UUID.randomUUID())

    private class Admin(
        override val player: UUID?,
    ) : CommandSender {
        val replies = mutableListOf<RichText>()

        override fun hasPermission(node: String) = true

        override fun reply(text: RichText) {
            replies += text
        }
    }

    init {
        hub.reload().join()
    }

    private fun run(line: String) = tree.execute(admin, line.split(" "))

    @Test
    fun `a new NPC belongs to this group and stands where the admin stands`() {
        run("npc create <yellow>Daily Rewards")

        val create = sent.single().create
        create.groupId shouldBe "lobby"
        create.title shouldBe "<yellow>Daily Rewards"
        create.location.x shouldBe 10.5
        sent.single().actingPlayerUuid shouldBe admin.player.toString()
    }

    @Test
    fun `changes patch only what they change, on the NPC found by the end of its id`() {
        run("npc title 2c3d4e5f off")
        run("npc rightaction 2c3d4e5f transfer bedwars least_filled")
        run("npc movehere 2c3d4e5f")

        sent[0].update.let { (it.hasTitle() to it.title to it.hasSubtitle()) } shouldBe (true to "" to false)
        sent[1]
            .update.rightClick.transfer.routingStrategy shouldBe RoutingStrategy.ROUTING_STRATEGY_LEAST_FILLED
        sent[1].update.hasTitle() shouldBe false
        sent[2].update.location.yaw shouldBe 90f
        sent.map { it.update.npcId }.toSet() shouldBe setOf(shop.npcId)
    }

    @Test
    fun `NPCs made in the same minute get names of their own`() {
        run("npc list")
        run("npc delete 7d6c5b4a")

        admin.replies.joinToString("\n").let {
            it shouldContain "2c3d4e5f"
            it shouldContain "7d6c5b4a"
        }
        sent.single().deleteNpcId shouldBe guide.npcId
    }

    @Test
    fun `mistakes are explained instead of sent`() {
        run("npc rightaction 2c3d4e5f transfer bedwars sideways")
        run("npc delete nothing")

        sent shouldBe emptyList()
        admin.replies shouldContain prefixed(Message.NPC_STRATEGY_INVALID, "strategy" to "sideways")
        admin.replies shouldContain prefixed(Message.NPC_NOT_FOUND, "npc" to "nothing")
    }

    @Test
    fun `teleport takes the admin to the NPC without asking anyone`() {
        run("npc teleport 2c3d4e5f")

        teleports.single().y shouldBe 64.0
        sent shouldBe emptyList()
        admin.replies shouldContain prefixed(Message.NPC_TELEPORTED, "npc" to "2c3d4e5f")
    }

    @Test
    fun `a teleport into a world this server lacks says so`() {
        run("npc teleport 11112222")

        teleports shouldBe emptyList()
        admin.replies shouldContain prefixed(Message.NPC_WORLD_MISSING, "world" to "void")
    }

    @Test
    fun `an invalid title is explained instead of sent, and the API never sees it`() {
        run("npc create <frog>Shop")
        run("npc title 2c3d4e5f {name} wins")

        sent shouldBe emptyList()
        admin.replies shouldContain
            prefixed(Message.NPC_TEXT_INVALID, "errors" to "unknown tag <frog> (at character 0)")
        admin.replies shouldContain
            prefixed(
                Message.NPC_TEXT_INVALID,
                "errors" to "{name} is no placeholder here; this text offers {players}, {servers} (at character 0)",
            )
    }

    @Test
    fun `a valid title and the off word reach the API`() {
        run("npc title 2c3d4e5f <gold>{players} playing")
        run("npc subtitle 2c3d4e5f off")

        sent.map { it.update.title } shouldBe listOf("<gold>{players} playing", "")
        sent.last().update.hasSubtitle() shouldBe true
    }

    private fun prefixed(
        message: Message,
        vararg values: Pair<String, Any?>,
    ) = messages.prefixed(message, *values)
}
