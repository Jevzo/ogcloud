package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.GameState
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.TransferTarget
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.ogwars.cloud.messaging.NonRetryableEventException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import io.ogwars.cloud.domain.server.GameState as DomainGameState

class ConsumerHandlersTest {
    private val lobby = group(id = "lobby")
    private val bedwars = group(id = "bedwars")
    private val smp = group(GroupType.STATIC, id = "smp")
    private val f = LifecycleFixture(listOf(lobby, bedwars, smp))
    private val events = Events(f.clock)
    private val sessions = ValkeySessionStore(TestValkey.connection)
    private val commands = ServerCommandHandler(f.lifecycle, f.groupStore)
    private val heartbeats = HeartbeatHandler(f.lifecycle)
    private val gameStates = GameStateHandler(f.lifecycle, f.network)
    private val activity = PlayerActivityHandler(sessions)

    @Test
    fun `a request creates the server under the requested id`() {
        val id = UUID.randomUUID()

        commands.handle(events.request(id, "bedwars"))
        commands.handle(events.request(id, "bedwars"))

        f.state(id) shouldBe ServerState.REQUESTED
        f.events.transitions.size shouldBe 1
    }

    @Test
    fun `malformed or unknown commands are not retried`() {
        shouldThrow<NonRetryableEventException> { commands.handle(events.request(UUID.randomUUID(), "ghost")) }
        shouldThrow<NonRetryableEventException> { commands.handle(events.request(UUID.randomUUID(), "Bad_Group")) }
        shouldThrow<NonRetryableEventException> {
            commands.handle(
                events
                    .request(UUID.randomUUID(), "bedwars")
                    .toBuilder()
                    .setServerId("x")
                    .build(),
            )
        }
    }

    @Test
    fun `template pushes are left to the gateway`() {
        val id = UUID.randomUUID()
        commands.handle(events.request(id, "bedwars"))

        commands.handle(events.push(id))

        f.state(id) shouldBe ServerState.REQUESTED
    }

    @Test
    fun `a STATIC group never gets a second server`() {
        val first = UUID.randomUUID()
        commands.handle(events.request(first, "smp"))

        commands.handle(events.request(UUID.randomUUID(), "smp"))

        f.servers.listByGroup(smp.id).map { it.value.id } shouldContainExactly listOf(first)
    }

    @Test
    fun `stop and kill reach the lifecycle with the admin as actor`() {
        val stopped = running(bedwars, players = 0)
        val killed = running(bedwars, players = 0)

        commands.handle(events.stop(stopped))
        commands.handle(events.kill(killed))

        f.state(stopped) shouldBe ServerState.STOPPING
        f.pods.deleted.map { it.second } shouldContainExactly listOf(null, Duration.ZERO)
    }

    @Test
    fun `the first heartbeat makes a server RUNNING and old ones do not roll counts back`() {
        val id = starting(bedwars)
        val late = events.heartbeat(id, players = 9)
        f.clock.advance(Duration.ofSeconds(5))

        heartbeats.handle(events.heartbeat(id, players = 4))
        heartbeats.handle(late)

        f.servers
            .get(id)
            ?.value
            ?.state shouldBe ServerState.RUNNING
        f.servers
            .get(id)
            ?.value
            ?.players shouldBe 4
    }

    @Test
    fun `game states update, and ENDING sends players to the default group and drains`() {
        val id = running(bedwars, players = 6)

        gameStates.handle(events.gameState(id, GameState.GAME_STATE_INGAME))
        f.servers
            .get(id)
            ?.value
            ?.gameState shouldBe DomainGameState.INGAME
        gameStates.handle(events.gameState(id, GameState.GAME_STATE_ENDING))

        f.events.transfers shouldContainExactly listOf(id to TransferTarget.ToGroup(GroupId("lobby")))
        f.state(id) shouldBe ServerState.DRAINING
    }

    @Test
    fun `an ENDING that was recorded but not drained drains on redelivery without a second transfer`() {
        val id = running(bedwars, players = 6)
        val current = f.servers.get(id) ?: error("missing")
        f.servers.compareAndSet(current.revision, current.value.copy(gameState = DomainGameState.ENDING))

        gameStates.handle(events.gameState(id, GameState.GAME_STATE_ENDING))

        f.state(id) shouldBe ServerState.DRAINING
        f.events.transfers shouldBe emptyList()
    }

    @Test
    fun `switches move the session and only the holding proxy's disconnect removes it`() {
        val player = UUID.randomUUID()
        val proxy = UUID.randomUUID()
        val backend = UUID.randomUUID()
        sessions.put(PlayerSession(player, "Steve", proxy, null, "default", 0, f.clock.instant()))

        activity.handle(events.switched(player, backend))
        sessions.get(player)?.value?.serverId shouldBe backend

        activity.handle(events.disconnected(player, UUID.randomUUID()))
        sessions.get(player)?.value?.proxyId shouldBe proxy
        activity.handle(events.disconnected(player, proxy))
        sessions.get(player) shouldBe null
    }

    private fun starting(group: Group): UUID {
        val id = f.request(group)
        f.lifecycle.podCreated(id)
        f.lifecycle.podStarted(id, "10.42.0.7")
        return id
    }

    private fun running(
        group: Group,
        players: Int,
    ): UUID = starting(group).also { heartbeats.handle(events.heartbeat(it, players)) }
}
