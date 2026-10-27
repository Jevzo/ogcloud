package io.ogwars.cloud.controller.login

import io.ogwars.cloud.contracts.events.v1.PlayerActivity
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.consume.Events
import io.ogwars.cloud.controller.consume.PlayerActivityHandler
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.controller.readmodel.InMemoryPermissionGroupStore
import io.ogwars.cloud.controller.readmodel.InMemoryPlayerStore
import io.ogwars.cloud.controller.readmodel.permissionGroup
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class LoginResolverTest {
    private val f = LifecycleFixture(listOf(group(id = "lobby")))
    private val players = InMemoryPlayerStore()
    private val permissionGroups =
        InMemoryPermissionGroupStore(
            permissionGroup("default", isDefault = true),
            permissionGroup(
                "staff",
                permissions = mapOf(LoginResolver.MAINTENANCE_BYPASS to "Join during maintenance"),
            ),
        )
    private val sessions = ValkeySessionStore(TestValkey.connection)
    private val readModels = ValkeyReadModelStore(TestValkey.connection)
    private val resolver = LoginResolver(players, permissionGroups, f.network, sessions, readModels, f.clock)
    private val player = UUID.randomUUID()
    private val proxyA = UUID.randomUUID()

    @Test
    fun `a first login records the player with the default rank and opens the session`() {
        val result = resolver.resolve(player, "Steve", proxyA)

        result.allowed shouldBe true
        result.profile.permissionGroupId shouldBe "default"
        sessions.get(player)?.value?.proxyId shouldBe proxyA
        PlayerProfile.parseFrom(readModels.get(ReadModelKind.PLAYER_PROFILE, player.toString())).playerName shouldBe
            "Steve"
    }

    @Test
    fun `maintenance lets only players with the bypass permission in`() {
        maintenance(on = true)
        val staff = UUID.randomUUID()
        players.recordSeen(staff, "Admin", f.clock.instant(), PlayerRank(staff, "staff", null))

        val denied = resolver.resolve(player, "Steve", proxyA)

        denied.allowed shouldBe false
        denied.kickMessage shouldBe
            f.network
                .load()
                .value.maintenanceKickMessage
        sessions.get(player) shouldBe null
        resolver.resolve(staff, "Admin", proxyA).allowed shouldBe true
    }

    @Test
    fun `with the permission system off the proxy decides about maintenance`() {
        maintenance(on = true, permissionSystem = false)

        resolver.resolve(player, "Steve", proxyA).allowed shouldBe true
    }

    @Test
    fun `an expired rank already counts as the default group`() {
        players.recordSeen(
            player,
            "Steve",
            f.clock.instant(),
            PlayerRank(player, "staff", f.clock.instant().plus(Duration.ofHours(1))),
        )
        f.clock.advance(Duration.ofHours(2))

        resolver.resolve(player, "Steve", proxyA).profile.permissionGroupId shouldBe "default"
    }

    @Test
    fun `a late disconnect from the old proxy keeps the session of a reconnect`() {
        val proxyB = UUID.randomUUID()
        val activity = PlayerActivityHandler(sessions)
        val disconnectFromA: PlayerActivity = Events(f.clock).disconnected(player, proxyA)

        resolver.resolve(player, "Steve", proxyA)
        resolver.resolve(player, "Steve", proxyB)
        activity.handle(disconnectFromA)

        sessions.get(player)?.value?.proxyId shouldBe proxyB
    }

    private fun maintenance(
        on: Boolean,
        permissionSystem: Boolean = true,
    ) {
        val current = f.network.load()
        f.network.save(
            current.revision,
            current.value.copy(maintenance = on, permissionSystemEnabled = permissionSystem),
        )
    }
}
