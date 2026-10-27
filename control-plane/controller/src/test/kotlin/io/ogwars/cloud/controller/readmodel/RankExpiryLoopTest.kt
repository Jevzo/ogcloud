package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.MutableClock
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.server.T0
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class RankExpiryLoopTest {
    private val clock = MutableClock(T0)
    private val players = InMemoryPlayerStore()
    private val sessions = ValkeySessionStore(TestValkey.connection)
    private val broadcasts = RecordingBroadcasts()
    private val permissions =
        PlayerPermissions(
            players,
            InMemoryPermissionGroupStore(permissionGroup("default", isDefault = true)),
            sessions,
            ValkeyReadModelStore(TestValkey.connection),
            broadcasts,
        )

    init {
        TestValkey.flush()
    }

    @Test
    fun `expired timed ranks fall back to the default group and reach the player's proxy`() {
        val expiring = UUID.randomUUID()
        val permanent = UUID.randomUUID()
        players.recordSeen(expiring, "Steve", T0, PlayerRank(expiring, "vip", T0.plus(Duration.ofDays(1))))
        players.recordSeen(permanent, "Alex", T0, PlayerRank(permanent, "vip", null))
        sessions.put(PlayerSession(expiring, "Steve", UUID.randomUUID(), null, "vip", 0, T0))
        clock.advance(Duration.ofDays(2))

        RankExpiryLoop(players, permissions, clock).runOnce()

        players.find(expiring)?.rank shouldBe PlayerRank(expiring, "default", null)
        players.find(permanent)?.rank?.groupId shouldBe "vip"
        sessions.get(expiring)?.value?.permissionGroupId shouldBe "default"
        broadcasts.sent.map { it.playerPermissions.permissionGroupId } shouldBe listOf("default")
    }
}
