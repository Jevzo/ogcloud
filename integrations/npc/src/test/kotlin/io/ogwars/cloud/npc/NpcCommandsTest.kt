package io.ogwars.cloud.npc

import io.ogwars.cloud.contracts.events.v1.RoutingStrategy
import io.ogwars.cloud.contracts.node.v1.Location
import io.ogwars.cloud.contracts.node.v1.NpcChangeRequest
import io.ogwars.cloud.node.core.command.CommandSender
import io.ogwars.cloud.node.core.command.CommandTree
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
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
            .setNpcId("0192d2c1-0000-7000-8000-00000000000a")
            .setGroupId("lobby")
            .setLocation(Location.newBuilder().setWorld("world").setY(64.0))
            .setTitle("&aShop")
            .build()
    private val queries = mockk<NodeQueries> { every { npcs("lobby") } returns completedFuture(listOf(shop)) }
    private val hub =
        NpcHub(RecordingPackets(), queries, "lobby", { _, _, _ -> }, mockk(relaxed = true), Clock.systemUTC())
    private val here = NpcLocation("world", 10.5, 70.0, -3.0, 90f, 0f)
    private val teleports = mutableListOf<NpcLocation>()
    private val sent = mutableListOf<NpcChangeRequest>()
    private val commands =
        NpcCommands(
            hub,
            messages,
            "lobby",
            object : NpcPlaces {
                override fun location(player: UUID) = here

                override fun teleport(
                    player: UUID,
                    location: NpcLocation,
                ) {
                    teleports += location
                }
            },
        ) { sent.add(it.build()) }
    private val tree = CommandTree(messages, commands.subcommands, Message.INFO)
    private val admin = Admin(UUID.randomUUID())

    private class Admin(
        override val player: UUID?,
    ) : CommandSender {
        val replies = mutableListOf<String>()

        override fun hasPermission(node: String) = true

        override fun reply(text: String) {
            replies += text
        }
    }

    init {
        hub.reload().join()
    }

    private fun run(line: String) = tree.execute(admin, line.split(" "))

    @Test
    fun `a new NPC belongs to this group and stands where the admin stands`() {
        run("npc create &eDaily Rewards")

        val create = sent.single().create
        create.groupId shouldBe "lobby"
        create.title shouldBe "&eDaily Rewards"
        create.location.x shouldBe 10.5
        sent.single().actingPlayerUuid shouldBe admin.player.toString()
    }

    @Test
    fun `changes edit a copy of the NPC found by the start of its id`() {
        run("npc title 0192d2c1 off")
        run("npc rightaction 0192d2c1 transfer bedwars least_filled")
        run("npc movehere 0192d2c1")

        sent[0].update.title shouldBe ""
        sent[1]
            .update.rightClick.transfer.routingStrategy shouldBe RoutingStrategy.ROUTING_STRATEGY_LEAST_FILLED
        sent[2].update.location.yaw shouldBe 90f
        sent.map { it.update.npcId }.toSet() shouldBe setOf(shop.npcId)
    }

    @Test
    fun `mistakes are explained instead of sent`() {
        run("npc rightaction 0192d2c1 transfer bedwars sideways")
        run("npc delete nothing")

        sent shouldBe emptyList()
        admin.replies shouldContain prefixed(Message.NPC_STRATEGY_INVALID, "strategy" to "sideways")
        admin.replies shouldContain prefixed(Message.NPC_NOT_FOUND, "npc" to "nothing")
    }

    @Test
    fun `teleport takes the admin to the NPC without asking anyone`() {
        run("npc teleport 0192d2c1")

        teleports.single().y shouldBe 64.0
        sent shouldBe emptyList()
    }

    private fun prefixed(
        message: Message,
        vararg values: Pair<String, Any?>,
    ) = messages.text(Message.PREFIX) + " " + messages.text(message, *values)
}
