package io.ogwars.cloud.controller.overview

import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.InMemoryGroupStore
import io.ogwars.cloud.controller.lifecycle.MutableClock
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.overview.GroupLoad
import io.ogwars.cloud.domain.overview.LoadHistory
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.T0
import io.ogwars.cloud.domain.server.server
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.livestate.ValkeyLoadHistoryStore
import io.ogwars.cloud.livestate.ValkeyServerStateStore
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class LoadSamplerTest {
    private val clock = MutableClock(T0)
    private val servers = ValkeyServerStateStore(TestValkey.connection)
    private val sessions = ValkeySessionStore(TestValkey.connection)
    private val history = ValkeyLoadHistoryStore(TestValkey.connection)
    private val groups = InMemoryGroupStore(listOf(group(id = "lobby"), group(GroupType.PROXY, id = "proxy")))
    private val sampler = LoadSampler(groups, servers, sessions, history, clock)

    init {
        TestValkey.flush()
    }

    @Test
    fun `each run records the online players and every group's load at the current time`() {
        servers.create(server(ServerState.RUNNING, players = 3, id = UUID.randomUUID()))
        servers.create(server(ServerState.STARTING, id = UUID.randomUUID()))
        repeat(3) { sessions.put(session("player$it")) }

        sampler.runOnce()
        clock.advance(LoadHistory.INTERVAL)
        sampler.runOnce()

        val samples = history.since(T0)
        samples.map { it.at } shouldBe listOf(T0, T0.plus(LoadHistory.INTERVAL))
        samples.last().players shouldBe 3
        samples.last().groups shouldBe
            mapOf(
                GroupId("lobby") to GroupLoad(running = 1, pending = 1, players = 3),
                GroupId("proxy") to GroupLoad.NONE,
            )
    }

    private fun session(name: String) =
        PlayerSession(
            playerUuid = UUID.randomUUID(),
            playerName = name,
            proxyId = UUID.randomUUID(),
            serverId = null,
            permissionGroupId = "default",
            rankVersion = 0,
            connectedAt = T0,
        )
}
