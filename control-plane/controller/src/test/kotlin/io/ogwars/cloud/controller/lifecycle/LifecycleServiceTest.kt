package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.server.ServerState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class LifecycleServiceTest {
    private val lobby = group(id = "lobby")
    private val f = LifecycleFixture(listOf(lobby))
    private val clock = f.clock
    private val servers = f.servers
    private val failures = f.failures
    private val pods = f.pods
    private val events = f.events
    private val properties = f.properties
    private val lifecycle = f.lifecycle

    @Test
    fun `a server walks from REQUESTED to RUNNING and each step is published once`() {
        val id = request()

        lifecycle.podCreated(id)
        lifecycle.podCreated(id)
        lifecycle.podStarted(id, "10.42.0.7")
        lifecycle.heartbeat(id, players = 0, maxPlayers = 50, at = clock.instant())

        val server = servers.get(id)?.value
        server?.state shouldBe ServerState.RUNNING
        server?.host shouldBe "10.42.0.7"
        server?.port shouldBe 25565
        events.transitions.map { it.second } shouldContainExactly
            listOf(ServerState.REQUESTED, ServerState.PREPARING, ServerState.STARTING, ServerState.RUNNING)
    }

    @Test
    fun `a repeated request keeps the first server`() {
        val id = request()
        lifecycle.request(id, lobby.id, GroupType.DYNAMIC, "paper-26.3", ACTOR)

        events.transitions.size shouldBe 1
    }

    @Test
    fun `occupied servers are not safe to evict`() {
        val id = running()

        lifecycle.heartbeat(id, players = 3, maxPlayers = 50, at = clock.instant())
        pods.safeToEvict[podName(id)] shouldBe false
        lifecycle.heartbeat(id, players = 0, maxPlayers = 50, at = clock.instant())
        pods.safeToEvict[podName(id)] shouldBe true
    }

    @Test
    fun `heartbeats of a stopping server change nothing`() {
        val id = running()
        lifecycle.kill(id, ACTOR, "test")

        lifecycle.heartbeat(id, players = 5, maxPlayers = 50, at = clock.instant())

        servers.get(id)?.value?.state shouldBe ServerState.STOPPING
        servers.get(id)?.value?.players shouldBe 0
    }

    @Test
    fun `a graceful stop drains an occupied server and stops an empty one`() {
        val occupied = running(players = 4)
        val empty = running(players = 0)

        lifecycle.stop(occupied, ACTOR, "scale down")
        lifecycle.stop(empty, ACTOR, "scale down")

        servers.get(occupied)?.value?.state shouldBe ServerState.DRAINING
        servers.get(empty)?.value?.state shouldBe ServerState.STOPPING
        pods.deleted shouldContainExactly listOf(podName(empty) to null)
    }

    @Test
    fun `an undrained server runs again`() {
        val id = running(players = 4)
        lifecycle.drain(id, ACTOR, "test")

        lifecycle.undrain(id, ACTOR, "load")

        servers.get(id)?.value?.state shouldBe ServerState.RUNNING
    }

    @Test
    fun `servers of a deleted group stop instead of draining`() {
        val id = running(players = 4)
        f.removeGroup(GroupId("lobby"))

        lifecycle.drain(id, ACTOR, "group deleted")

        servers.get(id)?.value?.state shouldBe ServerState.STOPPING
    }

    @Test
    fun `a kill deletes the pod without grace`() {
        val id = running()

        lifecycle.kill(id, ACTOR, "admin")

        pods.deleted shouldContainExactly listOf(podName(id) to Duration.ZERO)
    }

    @Test
    fun `an ended pod removes the server from live state`() {
        val id = running()

        lifecycle.podEnded(id, PodEnd.STOPPED, "pod deleted")

        servers.get(id) shouldBe null
        events.transitions.last() shouldBe Triple(ServerState.RUNNING, ServerState.STOPPED, "pod deleted")
    }

    @Test
    fun `failed starts pause the group after five failures`() {
        repeat(properties.crashLoop.pauseAfter) {
            val id = request()
            lifecycle.podCreated(id)
            lifecycle.podEnded(id, PodEnd.FAILED, "container failed")
        }

        failures.isPaused(lobby.id) shouldBe true
        events.pausedGroups shouldContainExactly listOf(lobby.id)
    }

    @Test
    fun `a STATIC group never gets a second server, whoever asks`() {
        val smp = group(GroupType.STATIC, id = "smp")
        val s = LifecycleFixture(listOf(smp))

        val first = s.lifecycle.request(UUID.randomUUID(), smp.id, smp.type, smp.profileId, ACTOR)
        val second = s.lifecycle.request(UUID.randomUUID(), smp.id, smp.type, smp.profileId, ACTOR)

        (first != null) shouldBe true
        second shouldBe null
        s.servers.listByGroup(smp.id).map { it.value.id } shouldBe listOf(first?.id)
    }

    @Test
    fun `a pending server we stopped does not count as a crash`() {
        repeat(properties.crashLoop.pauseAfter) {
            val id = request()
            lifecycle.podCreated(id)
            lifecycle.stopNow(id, ACTOR, "group deleted")
            lifecycle.podEnded(id, PodEnd.FAILED, "pod deleted while starting")
        }

        failures.failures(lobby.id) shouldBe emptyList()
    }

    @Test
    fun `an ended pod reported twice ends the server once`() {
        val id = running()

        lifecycle.podEnded(id, PodEnd.STOPPED, "pod deleted")
        lifecycle.podEnded(id, PodEnd.STOPPED, "pod deleted")

        events.transitions.count { it.second == ServerState.STOPPED } shouldBe 1
    }

    @Test
    fun `a server that ran does not count as a failed start`() {
        val id = running()

        lifecycle.podEnded(id, PodEnd.FAILED, "crashed later")

        failures.failures(lobby.id) shouldBe emptyList()
    }

    private fun request(): UUID = f.request(lobby)

    private fun running(players: Int = 0): UUID {
        val id = request()
        lifecycle.podCreated(id)
        lifecycle.podStarted(id, "10.42.0.7")
        lifecycle.heartbeat(id, players, maxPlayers = 50, at = clock.instant())
        return id
    }

    private fun podName(id: UUID) = servers.get(id)?.value?.podName ?: "lobby-${id.toString().replace("-", "")}"

    private companion object {
        val ACTOR = Actor.system("test")
    }
}
