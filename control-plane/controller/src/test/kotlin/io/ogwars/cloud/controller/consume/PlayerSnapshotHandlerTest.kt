package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.EventMetadata
import io.ogwars.cloud.contracts.events.v1.ProxyPlayersSnapshot
import io.ogwars.cloud.contracts.events.v1.SnapshotPlayer
import io.ogwars.cloud.contracts.toTimestamp
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.controller.login.LoginResolver
import io.ogwars.cloud.controller.readmodel.InMemoryPermissionGroupStore
import io.ogwars.cloud.controller.readmodel.InMemoryPlayerStore
import io.ogwars.cloud.controller.readmodel.permissionGroup
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PlayerSnapshotHandlerTest {
    private val f = LifecycleFixture(listOf(group(id = "lobby")))
    private val sessions = ValkeySessionStore(TestValkey.connection)
    private val logins =
        LoginResolver(
            InMemoryPlayerStore(),
            InMemoryPermissionGroupStore(permissionGroup("default", isDefault = true)),
            f.network,
            sessions,
            ValkeyReadModelStore(TestValkey.connection),
            f.clock,
        )
    private val handler = PlayerSnapshotHandler(sessions, logins)
    private val proxy = UUID.randomUUID()
    private val otherProxy = UUID.randomUUID()
    private val takenAt = f.clock.instant()

    private fun session(
        holder: UUID,
        connectedAt: Instant,
    ) = UUID.randomUUID().also { sessions.put(PlayerSession(it, "p", holder, null, "default", 0, connectedAt)) }

    private fun snapshot(vararg players: SnapshotPlayer) =
        ProxyPlayersSnapshot
            .newBuilder()
            .setMetadata(EventMetadata.newBuilder().setOccurredAt(takenAt.toTimestamp()))
            .setProxyId(proxy.toString())
            .addAllPlayers(players.toList())
            .build()

    @Test
    fun `a listed player without a session gets it back`() {
        val lost = UUID.randomUUID()
        val server = UUID.randomUUID()

        handler.handle(
            snapshot(
                SnapshotPlayer
                    .newBuilder()
                    .setPlayerUuid(
                        lost.toString(),
                    ).setPlayerName("Steve")
                    .setServerId(server.toString())
                    .build(),
            ),
        )

        val restored = sessions.get(lost)?.value
        restored?.proxyId shouldBe proxy
        restored?.serverId shouldBe server
        restored?.permissionGroupId shouldBe "default"
    }

    @Test
    fun `a restore leaves alone the session a login created since the snapshot read none`() {
        val player = UUID.randomUUID()
        sessions.put(PlayerSession(player, "Steve", otherProxy, null, "default", 0, takenAt))

        logins.restore(player, "Steve", proxy, null, takenAt) shouldBe false

        sessions.get(player)?.value?.proxyId shouldBe otherProxy
    }

    @Test
    fun `stale sessions beyond the first page are dropped too`() {
        val stale = List(STALE_PAGES) { session(proxy, takenAt.minus(Duration.ofMinutes(5))) }

        handler.handle(snapshot())

        stale.count { sessions.get(it) != null } shouldBe 0
    }

    @Test
    fun `a session whose login is still being completed is kept, though the snapshot does not list it yet`() {
        // The controller writes the session while the proxy still holds the login, before the proxy counts the player.
        val loggingIn = session(proxy, takenAt.minusSeconds(10))

        handler.handle(snapshot())

        sessions.get(loggingIn) shouldNotBe null
    }

    @Test
    fun `only this proxy's older sessions it no longer lists are dropped`() {
        val stale = session(proxy, takenAt.minus(Duration.ofMinutes(5)))
        val joinedSince = session(proxy, takenAt.plusSeconds(1))
        val elsewhere = session(otherProxy, takenAt.minus(Duration.ofMinutes(5)))
        val listed = session(proxy, takenAt.minus(Duration.ofMinutes(5)))

        handler.handle(
            snapshot(
                SnapshotPlayer
                    .newBuilder()
                    .setPlayerUuid(listed.toString())
                    .setPlayerName("p")
                    .build(),
            ),
        )

        sessions.get(stale) shouldBe null
        sessions.get(joinedSince) shouldNotBe null
        sessions.get(elsewhere) shouldNotBe null
        sessions.get(listed) shouldNotBe null
    }

    private companion object {
        // More than two of the handler's pages of 500.
        const val STALE_PAGES = 1_001
    }
}
