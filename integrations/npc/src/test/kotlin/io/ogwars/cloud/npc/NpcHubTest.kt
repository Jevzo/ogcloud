package io.ogwars.cloud.npc

import io.ogwars.cloud.contracts.events.v1.RoutingStrategy
import io.ogwars.cloud.contracts.events.v1.ServerState
import io.ogwars.cloud.contracts.node.v1.Location
import io.ogwars.cloud.contracts.node.v1.NpcAction
import io.ogwars.cloud.contracts.node.v1.NpcTransfer
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.text.StoredTexts
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.toLegacy
import com.github.retrooper.packetevents.protocol.player.User
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import io.ogwars.cloud.contracts.node.v1.Npc as ManagedNpc
import io.ogwars.cloud.sdk.RoutingStrategy as SdkRoutingStrategy

/** Records what each player was sent. */
class RecordingPackets : NpcPackets {
    val sent = mutableListOf<String>()

    override fun prepare(user: User) {
        sent += "prepare"
    }

    override fun spawn(
        user: User,
        npc: ShownNpc,
        lines: List<RichText>,
    ) {
        sent += "spawn ${npc.spec.id} ${lines.map { it.toLegacy() }}"
    }

    override fun rename(
        user: User,
        lineId: Int,
        text: RichText,
    ) {
        sent += "rename ${text.toLegacy()}"
    }

    override fun look(
        user: User,
        npc: ShownNpc,
        yaw: Float,
        pitch: Float,
    ) {
        sent += "look ${npc.spec.id}"
    }

    override fun despawn(
        user: User,
        npc: ShownNpc,
    ) {
        sent += "despawn ${npc.spec.id}"
    }
}

class NpcHubTest {
    private val packets = RecordingPackets()
    private val npcs = mutableListOf<ManagedNpc>()
    private val servers = mutableListOf<ServerInfo>()
    private val queries =
        mockk<NodeQueries> {
            every { npcs("lobby") } answers { completedFuture(npcs.toList()) }
            every { servers("bedwars") } answers { completedFuture(servers.toList()) }
        }
    private val transfers = mutableListOf<Triple<UUID, String, SdkRoutingStrategy?>>()
    private val tasks = mutableListOf<Runnable>()
    private val scheduler =
        mockk<ScheduledExecutorService>(relaxed = true) {
            val task = slot<Runnable>()
            every { scheduleAtFixedRate(capture(task), any(), any(), TimeUnit.MILLISECONDS) } answers {
                tasks += task.captured
                mockk<ScheduledFuture<*>>(relaxed = true)
            }
        }
    private var now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId?) = this

            override fun instant() = now
        }
    private val hub =
        NpcHub(packets, queries, "lobby", { player, group, strategy ->
            transfers +=
                Triple(player, group, strategy)
        }, scheduler, clock, StoredTexts())
    private val steve = UUID.randomUUID()
    private val shop =
        ManagedNpc
            .newBuilder()
            .setNpcId("0192d2c1-0000-7000-8000-00000000000a")
            .setGroupId("lobby")
            .setLocation(
                Location
                    .newBuilder()
                    .setWorld("world")
                    .setX(0.0)
                    .setY(64.0)
                    .setZ(0.0),
            ).setTitle("<green>Bedwars")
            .setSubtitle("{players} playing on {servers}")
            .setLookAtPlayers(true)
            .setRightClick(
                NpcAction.newBuilder().setTransfer(
                    NpcTransfer
                        .newBuilder()
                        .setGroupId(
                            "bedwars",
                        ).setRoutingStrategy(RoutingStrategy.ROUTING_STRATEGY_MOST_FILLED),
                ),
            ).build()
    private val shopId = NpcHub.MANAGED + shop.npcId

    private fun viewer(
        x: Double,
        world: String = "world",
    ) = NpcViewer(steve, NpcLocation(world, x, 64.0, 0.0), mockk(relaxed = true))

    @Test
    fun `managed NPCs show to players near them, and leave when they walk away or change worlds`() {
        npcs += shop
        hub.reload().join()

        hub.moved(viewer(100.0))
        hub.moved(viewer(3.0))
        hub.moved(viewer(20.0))
        hub.moved(viewer(3.0, world = "nether"))

        packets.sent shouldContainExactly
            listOf("prepare", "spawn $shopId [§aBedwars, 0 playing on 0]", "look $shopId", "despawn $shopId")
    }

    @Test
    fun `an NPC update replaces it for its viewers, and a removal takes it away`() {
        npcs += shop
        hub.reload().join()
        hub.moved(viewer(30.0))
        packets.sent.clear()

        npcs[0] =
            shop
                .toBuilder()
                .setTitle("<red>Bedwars")
                .setSubtitle("")
                .build()
        hub.reload().join()
        npcs.clear()
        hub.reload().join()

        packets.sent shouldContainExactly listOf("despawn $shopId", "spawn $shopId [§cBedwars]", "despawn $shopId")
    }

    @Test
    fun `a right click transfers with the NPC's strategy, once per click`() {
        npcs += shop
        hub.reload().join()
        hub.moved(viewer(3.0))
        val body = 1_500_000_000

        hub.clicked(steve, body, rightClick = true)
        hub.clicked(steve, body, rightClick = true)
        now = now.plusSeconds(1)
        hub.clicked(steve, body, rightClick = false)
        hub.clicked(steve, 42, rightClick = true)

        transfers shouldContainExactly listOf(Triple(steve, "bedwars", SdkRoutingStrategy.MOST_FILLED))
    }

    @Test
    fun `live counts of the click target fill the text`() {
        npcs += shop
        hub.reload().join()
        hub.moved(viewer(3.0))
        hub.start()
        servers +=
            ServerInfo
                .newBuilder()
                .setState(ServerState.SERVER_STATE_RUNNING)
                .setPlayers(7)
                .build()
        servers +=
            ServerInfo
                .newBuilder()
                .setState(ServerState.SERVER_STATE_STARTING)
                .setPlayers(1)
                .build()

        tasks.forEach(Runnable::run)

        packets.sent.takeLast(2) shouldContainExactly listOf("rename §aBedwars", "rename 7 playing on 1")
    }

    @Test
    fun `NPCs face players and are found by where players look`() {
        val (yaw, pitch) = NpcHub.facing(NpcLocation("world", 0.0, 64.0, 0.0), NpcLocation("world", 0.0, 64.0, 5.0))
        yaw shouldBe 0f
        pitch shouldBe 0f
        NpcHub.facing(NpcLocation("world", 0.0, 64.0, 0.0), NpcLocation("world", 5.0, 64.0, 0.0)).first shouldBe -90f

        npcs += shop
        hub.reload().join()
        hub.lookedAt(NpcLocation("world", 0.0, 64.0, -4.0, yaw = 0f, pitch = 0f))?.npcId shouldBe shop.npcId
        hub.lookedAt(NpcLocation("world", 0.0, 64.0, -4.0, yaw = 180f, pitch = 0f)) shouldBe null
        hub.findManaged("0000000A") shouldBe shop
    }
}
