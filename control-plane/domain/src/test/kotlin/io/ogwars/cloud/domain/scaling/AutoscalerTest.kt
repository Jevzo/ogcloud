package io.ogwars.cloud.domain.scaling

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.group.scaling
import io.ogwars.cloud.domain.scaling.ScalingDecision.Drain
import io.ogwars.cloud.domain.scaling.ScalingDecision.Start
import io.ogwars.cloud.domain.scaling.ScalingDecision.Undrain
import io.ogwars.cloud.domain.scaling.ScalingReason.LOAD
import io.ogwars.cloud.domain.scaling.ScalingReason.MIN_ONLINE
import io.ogwars.cloud.domain.scaling.ScalingReason.SCALE_DOWN
import io.ogwars.cloud.domain.scaling.ScalingReason.STATIC
import io.ogwars.cloud.domain.server.GameState
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.T0
import io.ogwars.cloud.domain.server.server
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AutoscalerTest {
    private val autoscaler = Autoscaler()

    private fun decide(
        group: Group,
        vararg servers: Server,
        lastScaledAt: Instant? = null,
        networkMaintenance: Boolean = false,
        paused: Boolean = false,
    ) = autoscaler.decide(GroupSnapshot(group, servers.toList(), lastScaledAt, networkMaintenance, paused), T0)

    private fun id(n: Int): UUID = UUID(0, n.toLong())

    @Nested
    inner class Skips {
        @Test
        fun `nothing happens during network or group maintenance, cooldown or a crash-loop pause`() {
            val empty = group(scaling = scaling(min = 2))

            decide(empty, networkMaintenance = true).shouldBeEmpty()
            decide(group(scaling = scaling(min = 2), maintenance = true)).shouldBeEmpty()
            decide(empty, lastScaledAt = T0.minusSeconds(59)).shouldBeEmpty()
            decide(empty, paused = true).shouldBeEmpty()
            decide(empty, lastScaledAt = T0.minusSeconds(60)) shouldContainExactly List(2) { Start(MIN_ONLINE) }
        }
    }

    @Nested
    inner class Dynamic {
        private val lobby = group(scaling = scaling(min = 2, max = 4, players = 10))

        @Test
        fun `pending servers count toward minOnline`() {
            decide(lobby, server(state = ServerState.STARTING)) shouldContainExactly listOf(Start(MIN_ONLINE))
        }

        @Test
        fun `servers in a game are not lobby capacity`() {
            val ingame = server(gameState = GameState.INGAME, players = 8, id = id(1))
            decide(lobby, ingame) shouldContainExactly List(2) { Start(MIN_ONLINE) }
        }

        @Test
        fun `minOnline respects maxInstances`() {
            val full = group(scaling = scaling(min = 3, max = 3, players = 10))
            val ingame = (1..2).map { server(gameState = GameState.INGAME, id = id(it)) }.toTypedArray()

            decide(full, *ingame) shouldContainExactly listOf(Start(MIN_ONLINE))
        }

        @Test
        fun `load above the threshold starts one server`() {
            decide(lobby, server(players = 9, id = id(1)), server(players = 8, id = id(2))) shouldContainExactly
                listOf(Start(LOAD))
        }

        @Test
        fun `no scale-up beyond maxInstances`() {
            val busy = (1..4).map { server(players = 10, id = id(it)) }.toTypedArray()
            decide(lobby, *busy).shouldBeEmpty()
        }

        @Test
        fun `low load drains the youngest empty lobbies above minOnline`() {
            val old = server(players = 0, id = id(1), startedAt = T0.minusSeconds(300))
            val young = server(players = 0, id = id(2), startedAt = T0.minusSeconds(60))
            val busy = server(players = 1, id = id(3), startedAt = T0.minusSeconds(30))

            decide(lobby, old, young, busy) shouldContainExactly listOf(Drain(id(2), SCALE_DOWN))
        }

        @Test
        fun `servers with players are never drained for low load`() {
            val lobbies = (1..3).map { server(players = 1, id = id(it)) }.toTypedArray()
            decide(lobby, *lobbies).shouldBeEmpty()
        }
    }

    @Nested
    inner class Proxy {
        private val proxies =
            group(type = GroupType.PROXY, id = "proxy", scaling = scaling(min = 2, max = 4, players = 100))

        private fun proxy(
            n: Int,
            players: Int = 0,
            state: ServerState = ServerState.RUNNING,
        ) = server(state = state, groupType = GroupType.PROXY, group = "proxy", players = players, id = id(n))

        @Test
        fun `minOnline undrains draining proxies before starting new ones`() {
            val draining = proxy(2, players = 5, state = ServerState.DRAINING)

            decide(proxies, draining) shouldContainExactly listOf(Undrain(id(2), MIN_ONLINE), Start(MIN_ONLINE))
        }

        @Test
        fun `minOnline start is all or nothing within maxInstances`() {
            val tight = group(type = GroupType.PROXY, id = "proxy", scaling = scaling(min = 3, max = 3, players = 100))
            val draining = (1..2).map { proxy(it, state = ServerState.DRAINING) }.toTypedArray()

            decide(tight, proxy(3), *draining) shouldContainExactly
                listOf(Undrain(id(1), MIN_ONLINE), Undrain(id(2), MIN_ONLINE))
        }

        @Test
        fun `high load undrains first, otherwise starts`() {
            decide(proxies, proxy(1, 90), proxy(2, 90), proxy(3, state = ServerState.DRAINING)) shouldContainExactly
                listOf(Undrain(id(3), LOAD))
            decide(proxies, proxy(1, 90), proxy(2, 90)) shouldContainExactly listOf(Start(LOAD))
        }

        @Test
        fun `low load drains the quietest proxy above minOnline`() {
            decide(proxies, proxy(1, 10), proxy(2, 3), proxy(3, 20)) shouldContainExactly
                listOf(Drain(id(2), SCALE_DOWN))
            decide(proxies, proxy(1, 0), proxy(2, 0)).shouldBeEmpty()
        }
    }

    @Nested
    inner class Static {
        private val smp = group(type = GroupType.STATIC, id = "smp", scaling = scaling(min = 1, max = 1))

        @Test
        fun `starts the one server when none is active, ignoring cooldown`() {
            decide(smp, lastScaledAt = T0) shouldContainExactly listOf(Start(STATIC))
            decide(
                smp,
                server(groupType = GroupType.STATIC, group = "smp", state = ServerState.STARTING),
            ).shouldBeEmpty()
        }

        @Test
        fun `minOnline zero keeps it off`() {
            val off = group(type = GroupType.STATIC, id = "smp", scaling = scaling(min = 0, max = 1))
            decide(off).shouldBeEmpty()
        }
    }
}
