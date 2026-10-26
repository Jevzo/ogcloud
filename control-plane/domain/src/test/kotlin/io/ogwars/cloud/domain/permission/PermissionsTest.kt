package io.ogwars.cloud.domain.permission

import io.ogwars.cloud.domain.server.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class PermissionsTest {
    private val player = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `groups grant exact nodes, or everything with a wildcard`() {
        val mod = group(setOf("ogcloud.admin.server"))

        mod.grants("ogcloud.admin.server") shouldBe true
        mod.grants("ogcloud.admin.group") shouldBe false
        group(setOf("*")).grants("anything.at.all") shouldBe true
    }

    @Test
    fun `timed ranks expire back to the default group`() {
        val rank = PlayerRank.assign(player, "vip", RankDuration.Timed(Duration.ofDays(30)), T0)

        rank.expiresAt shouldBe T0.plus(Duration.ofDays(30))
        rank.effectiveGroupId(T0.plus(Duration.ofDays(29)), "default") shouldBe "vip"
        rank.effectiveGroupId(T0.plus(Duration.ofDays(30)), "default") shouldBe "default"
    }

    @Test
    fun `permanent ranks never expire`() {
        val rank = PlayerRank.assign(player, "admin", RankDuration.Permanent, T0)

        rank.expiresAt shouldBe null
        rank.isExpired(T0.plus(Duration.ofDays(10_000))) shouldBe false
    }

    private fun group(permissions: Set<String>) =
        PermissionGroup(
            "mod",
            "Moderator",
            weight = 50,
            permissions = permissions,
            display = RankDisplay(),
            isDefault = false,
        )
}
