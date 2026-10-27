package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.GameState
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors

class ValkeyServerStateStoreTest : ValkeyTest() {
    private val store by lazy { ValkeyServerStateStore(valkey) }

    private fun server(
        n: Long,
        group: String = "lobby",
    ) = Server(
        id = UUID(0, n),
        groupId = GroupId(group),
        groupType = GroupType.DYNAMIC,
        profileId = "paper-26.3",
        state = ServerState.RUNNING,
        stateSince = NOW,
        players = 3,
        maxPlayers = 50,
        gameState = GameState.LOBBY,
        startedAt = NOW,
        lastHeartbeatAt = NOW.plusSeconds(5),
        host = "10.42.0.7",
        port = 25565,
    )

    @Test
    fun `creates once and round-trips every field`() {
        store.create(server(1)) shouldBe true
        store.create(server(1)) shouldBe false

        store.get(UUID(0, 1))?.value shouldBe server(1)
        store.get(UUID(0, 1))?.revision shouldBe 1
    }

    @Test
    fun `compare-and-set only replaces the expected revision`() {
        store.create(server(1))

        store.compareAndSet(1, server(1).copy(players = 10)) shouldBe true
        store.compareAndSet(1, server(1).copy(players = 20)) shouldBe false

        store.get(UUID(0, 1))?.value?.players shouldBe 10
        store.get(UUID(0, 1))?.revision shouldBe 2
    }

    @Test
    fun `exactly one of many concurrent writers wins`() {
        store.create(server(1))
        val executor = Executors.newFixedThreadPool(WRITERS)

        val wins =
            (1..WRITERS)
                .map { n -> executor.submit<Boolean> { store.compareAndSet(1, server(1).copy(players = n)) } }
                .count { it.get() }

        executor.shutdown()
        wins shouldBe 1
    }

    @Test
    fun `lists by group and refreshes the record ttl`() {
        store.create(server(1, "lobby"))
        store.create(server(2, "bedwars"))

        store.listByGroup(GroupId("lobby")).map { it.value.id } shouldContainExactly listOf(UUID(0, 1))
        store.listAll().size shouldBe 2
        valkey.sync().pttl(ValkeyKeys.server(UUID(0, 1))) shouldBeGreaterThan 0
    }

    @Test
    fun `expired records drop out of the indexes`() {
        store.create(server(1))
        valkey.sync().del(ValkeyKeys.server(UUID(0, 1)))

        store.listAll() shouldBe emptyList()
        valkey.sync().smembers(ValkeyKeys.SERVERS) shouldBe emptySet()
    }

    @Test
    fun `one unreadable record is skipped instead of breaking the listing`() {
        store.create(server(1))
        store.create(server(2))
        valkey.sync().hset(ValkeyKeys.server(UUID(0, 2)), ValkeyKeys.FIELD_DATA, byteArrayOf(0x0a, 0x7f, 0x01))

        store.listAll().map { it.value.id } shouldContainExactly listOf(UUID(0, 1))
    }

    @Test
    fun `createAlone refuses a second server of a group until the first is gone`() {
        val first = server(1).copy(groupType = GroupType.STATIC, gameState = null)
        val second = server(2).copy(groupType = GroupType.STATIC, gameState = null)

        store.createAlone(first) shouldBe true
        store.createAlone(second) shouldBe false
        // A member whose record expired without its index entry no longer blocks the group.
        valkey.sync().del(ValkeyKeys.server(first.id))
        store.createAlone(second) shouldBe true
    }

    @Test
    fun `delete removes the record and its index entries`() {
        store.create(server(1))

        store.delete(server(1))

        store.get(UUID(0, 1)) shouldBe null
        store.listByGroup(GroupId("lobby")) shouldBe emptyList()
    }

    private companion object {
        const val WRITERS = 16
    }
}
