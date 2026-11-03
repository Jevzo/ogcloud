package io.ogwars.cloud.livestate

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

class NpcMessagesTest {
    @Test
    fun `an NPC survives the round trip, with and without optional parts`() {
        val full =
            Npc(
                UUID.randomUUID(),
                GroupId("lobby"),
                Location("world", 1.5, 64.0, -3.25, 90f, 10f),
                "&aShop",
                "%players% online",
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
