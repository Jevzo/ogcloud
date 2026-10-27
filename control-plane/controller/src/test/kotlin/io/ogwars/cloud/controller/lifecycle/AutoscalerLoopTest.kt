package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.group.scaling
import io.ogwars.cloud.domain.scaling.Autoscaler
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.livestate.ValkeyScalingCooldownStore
import io.ogwars.cloud.persistence.JdbcScalingLogStore
import io.ogwars.cloud.persistence.TestDatabase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class AutoscalerLoopTest {
    private val meters = SimpleMeterRegistry()

    @Test
    fun `starts servers up to minOnline and logs every decision`() {
        val group = group(id = unique("min"), scaling = scaling(min = 2))
        val f = LifecycleFixture(listOf(group))

        loop(f).runOnce()

        f.servers.listByGroup(group.id).map { it.value.state } shouldBe List(2) { ServerState.REQUESTED }
        loggedDecisions(group.id) shouldBe 2
        f.events.scalings.size shouldBe 2
    }

    @Test
    fun `the cooldown holds back the next start until it expires`() {
        val group = group(id = unique("load"), scaling = scaling(min = 1, cooldown = Duration.ofSeconds(60)))
        val f = LifecycleFixture(listOf(group))
        running(f, group, players = 45)

        loop(f).runOnce()
        loop(f).runOnce()
        val afterCooldown = f.servers.listByGroup(group.id).size
        f.clock.advance(Duration.ofSeconds(61))
        loop(f).runOnce()

        afterCooldown shouldBe 2
        f.servers.listByGroup(group.id).size shouldBe 3
    }

    @Test
    fun `maintenance pauses scaling while drain timeouts still run`() {
        val group = group(id = unique("maint"), scaling = scaling(min = 2), drainTimeout = Duration.ofSeconds(30))
        val f = LifecycleFixture(listOf(group(id = "lobby"), group), maintenance = true)
        val draining = running(f, group, players = 3)
        f.lifecycle.drain(draining, ACTOR, "test")
        f.clock.advance(Duration.ofSeconds(31))
        f.lifecycle.heartbeat(draining, players = 3, maxPlayers = 50, at = f.clock.instant())

        loop(f).runOnce()
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
            SimpleMeterRegistry(),
        ).runOnce()

        f.servers.listByGroup(group.id).size shouldBe 1
        f.events.transfers.map { it.first } shouldBe listOf(draining)
    }

    @Test
    fun `a crash-loop paused group gets no servers`() {
        val group = group(id = unique("paused"), scaling = scaling(min = 2))
        val f = LifecycleFixture(listOf(group))
        f.failures.pause(group.id)

        loop(f).runOnce()

        f.servers.listByGroup(group.id).shouldBeEmpty()
    }

    @Test
    fun `one failing group does not stop the others`() {
        val broken = group(id = unique("broken"), scaling = scaling(min = 1))
        val healthy = group(id = unique("healthy"), scaling = scaling(min = 1))
        val f = LifecycleFixture(listOf(broken, healthy))
        val events = FailingFor(broken.id, f.events)

        loop(f, events).runOnce()

        f.servers.listByGroup(healthy.id).size shouldBe 1
        meters.find("ogcloud.controller.scaling.failures").counter()?.count() shouldBe 1.0
    }

    private fun loop(
        f: LifecycleFixture,
        events: ServerEvents = f.events,
    ) = AutoscalerLoop(
        f.servers,
        f.groupStore,
        f.network,
        ValkeyScalingCooldownStore(TestValkey.connection),
        f.failures,
        Autoscaler(),
        f.lifecycle,
        JdbcScalingLogStore(TestDatabase.jdbc),
        events,
        IdGenerator { UUID.randomUUID() },
        f.properties,
        f.clock,
        meters,
    )

    private fun running(
        f: LifecycleFixture,
        group: Group,
        players: Int,
    ): UUID {
        val id = f.request(group)
        f.lifecycle.podCreated(id)
        f.lifecycle.podStarted(id, "10.42.0.7")
        f.lifecycle.heartbeat(id, players, maxPlayers = 50, at = f.clock.instant())
        return id
    }

    private fun loggedDecisions(groupId: GroupId): Long =
        TestDatabase.jdbc
            .sql("select count(*) from scaling_log where group_id = :group")
            .param("group", groupId.value)
            .query(Long::class.java)
            .single()

    /** The scaling log is shared by every test in the JVM, so each test scales its own groups. */
    private fun unique(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}"

    private class FailingFor(
        private val groupId: GroupId,
        private val delegate: ServerEvents,
    ) : ServerEvents by delegate {
        override fun scalingDecided(entry: ScalingLogEntry) {
            check(entry.groupId != groupId) { "simulated failure for $groupId" }
            delegate.scalingDecided(entry)
        }
    }

    private companion object {
        val ACTOR = Actor.system("test")
    }
}
