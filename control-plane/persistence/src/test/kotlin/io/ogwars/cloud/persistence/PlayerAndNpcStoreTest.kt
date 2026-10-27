package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.ScalingPolicy
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.npc.NpcAction
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NpcStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.UUID

@PersistenceTest
class PlayerAndNpcStoreTest(
    @Autowired private val players: PlayerStore,
    @Autowired private val npcs: NpcStore,
    @Autowired private val groups: GroupStore,
) {
    private val uuid = UUID.fromString("00000000-0000-0000-0000-00000000abcd")

    @Test
    fun `first sight creates the player, later sights refresh name and time but keep the rank`() {
        val created = players.recordSeen(uuid, "Jevzo", NOW, PlayerRank(uuid, "default", null))
        created.firstSeenAt shouldBe NOW

        val later = players.recordSeen(uuid, "JevzoRenamed", NOW.plusSeconds(60), PlayerRank(uuid, "default", null))

        later.name shouldBe "JevzoRenamed"
        later.firstSeenAt shouldBe NOW
        later.lastSeenAt shouldBe NOW.plusSeconds(60)
        players.findByName("jevzorenamed")?.uuid shouldBe uuid
    }

    @Test
    fun `known players are listed by uuid and narrowed by name, wildcards taken literally`() {
        val first = UUID(0, 1)
        val second = UUID(0, 2)
        players.recordSeen(second, "Jevzo_2", NOW, PlayerRank(second, "default", null))
        players.recordSeen(first, "jevzo", NOW, PlayerRank(first, "default", null))
        players.recordSeen(uuid, "Notch", NOW, PlayerRank(uuid, "default", null))

        players.list(namePrefix = "JEV", after = null, limit = 10).map { it.uuid } shouldContainExactly
            listOf(first, second)
        players.list(namePrefix = "jevzo_", after = null, limit = 10).map { it.uuid } shouldContainExactly
            listOf(second)
        players.list(namePrefix = "%", after = null, limit = 10) shouldBe emptyList()
        players.list(namePrefix = null, after = first, limit = 1).map { it.uuid } shouldContainExactly listOf(second)
    }

    @Test
    fun `rank changes bump the version and expired ranks are found`() {
        players.recordSeen(uuid, "Jevzo", NOW, PlayerRank(uuid, "default", null))

        val version = players.updateRank(PlayerRank(uuid, "default", NOW.minusSeconds(1)))

        version shouldBe 1
        players.withExpiredRanks(NOW, limit = 10).map { it.uuid } shouldContainExactly listOf(uuid)
        players.resetExpiredRank(uuid, "default", NOW) shouldBe 2
        players.find(uuid)?.rank?.expiresAt shouldBe null
    }

    @Test
    fun `a rank an admin changed since it expired is not reset`() {
        val uuid = UUID.randomUUID()
        players.recordSeen(uuid, "Steve", NOW, PlayerRank(uuid, "default", null))
        players.updateRank(PlayerRank(uuid, "default", NOW.minusSeconds(1)))
        players.updateRank(PlayerRank(uuid, "default", null))

        players.resetExpiredRank(uuid, "default", NOW) shouldBe null
        players.find(uuid)?.rankVersion shouldBe 2
    }

    @Test
    fun `npcs keep their click actions`() {
        groups.create(
            Group(
                GroupId("hub"),
                GroupType.DYNAMIC,
                "paper-26.3",
                ScalingPolicy(1, 2, 50, 0.8, 0.3, Duration.ZERO),
                Duration.ZERO,
                false,
            ),
        )
        val npc =
            Npc(
                id = UUID.randomUUID(),
                groupId = GroupId("hub"),
                location = Location("world", 1.5, 64.0, -3.5, 90f, 0f),
                title = "&aBedwars",
                leftClick = NpcAction.Transfer(GroupId("bedwars"), RoutingStrategy.MOST_FILLED),
                rightClick = NpcAction.Transfer(GroupId("bedwars"), null),
            )

        npcs.save(npc)
        npcs.save(npc.copy(title = "&aBedwars 4x4"))

        npcs.findByGroup(GroupId("hub")) shouldContainExactly listOf(npc.copy(title = "&aBedwars 4x4"))
        npcs.findAll() shouldContainExactly listOf(npc.copy(title = "&aBedwars 4x4"))
        npcs.delete(npc.id)
        npcs.find(npc.id) shouldBe null
    }
}
