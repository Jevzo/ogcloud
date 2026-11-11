package io.ogwars.cloud.domain.overview

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.T0
import io.ogwars.cloud.domain.server.server
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class LoadTest {
    private val lobby = GroupId("lobby")
    private val bedwars = GroupId("bedwars")

    @Test
    fun `a group's load counts running and pending servers and the players on them`() {
        val servers =
            listOf(
                server(ServerState.RUNNING, players = 12, id = UUID.randomUUID()),
                server(ServerState.DRAINING, players = 3, id = UUID.randomUUID()),
                server(ServerState.STARTING, id = UUID.randomUUID()),
                server(ServerState.REQUESTED, id = UUID.randomUUID()),
                server(ServerState.RUNNING, players = 7, group = "bedwars", id = UUID.randomUUID()),
            )

        GroupLoad.byGroup(servers) shouldBe
            mapOf(
                lobby to GroupLoad(running = 1, pending = 2, players = 15),
                bedwars to GroupLoad(running = 1, pending = 0, players = 7),
            )
    }

    @Test
    fun `a sample names every group, idle ones with no load, and only those groups`() {
        val servers =
            listOf(
                server(ServerState.RUNNING, players = 4, id = UUID.randomUUID()),
                server(ServerState.RUNNING, players = 9, group = "deleted", id = UUID.randomUUID()),
            )

        LoadSample.of(T0, onlinePlayers = 13, groupIds = listOf(lobby, bedwars), servers = servers) shouldBe
            LoadSample(
                at = T0,
                players = 13,
                groups = mapOf(lobby to GroupLoad(running = 1, pending = 0, players = 4), bedwars to GroupLoad.NONE),
            )
    }
}
