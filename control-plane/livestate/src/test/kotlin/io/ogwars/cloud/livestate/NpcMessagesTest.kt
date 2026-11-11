package io.ogwars.cloud.livestate

import io.ogwars.cloud.contracts.node.v1.NpcPatch
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.npc.NpcAction
import io.ogwars.cloud.domain.npc.NpcSkin
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import io.ogwars.cloud.contracts.node.v1.NpcAction as NpcActionMessage

class NpcMessagesTest {
    @Test
    fun `an NPC survives the round trip, with and without optional parts`() {
        val full =
            Npc(
                UUID.randomUUID(),
                GroupId("lobby"),
                Location("world", 1.5, 64.0, -3.25, 90f, 10f),
                "<green>Shop",
                "{players} online",
                NpcSkin("texture", "signature"),
                lookAtPlayers = false,
                leftClick = NpcAction.Transfer(GroupId("bedwars"), RoutingStrategy.ROUND_ROBIN),
                rightClick = NpcAction.Transfer(GroupId("skywars"), routingStrategy = null),
            )
        val bare = Npc(UUID.randomUUID(), GroupId("lobby"), Location("world", 0.0, 0.0, 0.0))

        full.toProto().toDomain() shouldBe full
        bare.toProto().toDomain() shouldBe bare
    }

    @Test
    fun `a patch changes what it names and keeps the rest`() {
        val stored =
            Npc(
                UUID.randomUUID(),
                GroupId("lobby"),
                Location("world", 0.0, 0.0, 0.0),
                "Shop",
                "Open",
                NpcSkin("texture", "signature"),
                leftClick = NpcAction.Transfer(GroupId("bedwars"), routingStrategy = null),
            )

        val retitled =
            NpcPatch
                .newBuilder()
                .setTitle("Shop 2")
                .build()
                .applyTo(stored)
        val cleared =
            NpcPatch
                .newBuilder()
                .setSkinTexture("")
                .setLeftClick(NpcActionMessage.getDefaultInstance())
                .setLookAtPlayers(false)
                .build()
                .applyTo(stored)

        retitled shouldBe stored.copy(title = "Shop 2")
        cleared shouldBe stored.copy(skin = null, leftClick = null, lookAtPlayers = false)
    }

    @Test
    fun `a malformed id is refused`() {
        val message =
            Npc(UUID.randomUUID(), GroupId("lobby"), Location("world", 0.0, 0.0, 0.0))
                .toProto()
                .toBuilder()
                .setNpcId("shop")
                .build()

        shouldThrow<IllegalArgumentException> { message.toDomain() }
    }
}
