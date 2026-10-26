package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.TransferTarget
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class DeadlineSweeperTest {
    private val bedwars = group(id = "bedwars", drainTimeout = Duration.ofSeconds(60))
    private val f = LifecycleFixture(listOf(group(id = "lobby"), bedwars))
    private val sweeper =
        DeadlineSweeper(
            f.servers,
            f.groupStore,
            f.network,
            f.policy,
            f.lifecycle,
            f.pods,
            f.events,
            f.properties,
            f.clock,
        )

    @Test
    fun `a server stuck in PREPARING is removed and counts as a failed start`() {
        val id = f.request(bedwars)
        f.lifecycle.podCreated(id)
        f.clock.advance(Duration.ofMinutes(4))

        sweeper.runOnce()

        f.state(id) shouldBe null
        f.pods.deleted
            .single()
            .second shouldBe Duration.ZERO
        f.failures.failures(bedwars.id).size shouldBe 1
    }

    @Test
    fun `a running server without heartbeats is stopped`() {
        val id = running(players = 0)
        f.clock.advance(Duration.ofSeconds(31))

        sweeper.runOnce()

        f.state(id) shouldBe ServerState.STOPPING
    }

    @Test
    fun `players of a drained-out server are transferred once`() {
        val id = running(players = 5)
        f.lifecycle.drain(id, ACTOR, "scale down")
        f.clock.advance(Duration.ofSeconds(61))
        f.lifecycle.heartbeat(id, players = 5, maxPlayers = 50, at = f.clock.instant())

        sweeper.runOnce()
        sweeper.runOnce()

        f.events.transfers shouldContainExactly listOf(id to TransferTarget.ToGroup(GroupId("lobby")))
    }

    @Test
    fun `servers of a deleted group stop`() {
        val id = running(players = 0)
        f.removeGroup(bedwars.id)

        sweeper.runOnce()

        f.state(id) shouldBe ServerState.STOPPING
    }

    private fun running(players: Int): UUID {
        val id = f.request(bedwars)
        f.lifecycle.podCreated(id)
        f.lifecycle.podStarted(id, "10.42.0.7")
        f.lifecycle.heartbeat(id, players, maxPlayers = 50, at = f.clock.instant())
        return id
    }

    private companion object {
        val ACTOR = Actor.system("test")
    }
}
