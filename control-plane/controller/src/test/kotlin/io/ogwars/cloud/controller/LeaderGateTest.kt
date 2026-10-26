package io.ogwars.cloud.controller

import io.ogwars.cloud.controller.config.ControllerProperties
import io.ogwars.cloud.controller.config.LeaderElectionProperties
import io.ogwars.cloud.controller.leader.LeaderGate
import io.ogwars.cloud.controller.leader.LeaderLoop
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class LeaderGateTest {
    private val gates = mutableListOf<Replica>()

    @AfterEach
    fun stop() {
        gates.forEach { it.close() }
    }

    @Test
    fun `exactly one replica leads and runs the loops`() {
        val (a, b) = replicas("one-leader")

        runBlocking { eventually(ELECTION_TIMEOUT) { listOf(a, b).count { it.gate.isLeader } shouldBe 1 } }
        val (leader, standby) = if (a.gate.isLeader) a to b else b to a
        runBlocking { eventually(ELECTION_TIMEOUT) { leader.loop.runs.get() shouldBeGreaterThan 0 } }

        standby.loop.runs.get() shouldBe 0
    }

    @Test
    fun `a standby takes over when the leader stops`() {
        val (a, b) = replicas("takeover")
        runBlocking { eventually(ELECTION_TIMEOUT) { listOf(a, b).count { it.gate.isLeader } shouldBe 1 } }
        val (leader, standby) = if (a.gate.isLeader) a to b else b to a

        leader.gate.stop()

        runBlocking { eventually(ELECTION_TIMEOUT) { standby.gate.isLeader shouldBe true } }
        runBlocking { eventually(ELECTION_TIMEOUT) { standby.loop.runs.get() shouldBeGreaterThan 0 } }
        leader.gate.isLeader shouldBe false
        val stoppedAt = leader.loop.runs.get()
        runBlocking { eventually(ELECTION_TIMEOUT) { standby.loop.runs.get() shouldBeGreaterThan stoppedAt + 2 } }
        leader.loop.runs.get() shouldBe stoppedAt
    }

    @Test
    fun `a failing loop is counted and keeps running`() {
        val replica = Replica("failing", "solo", CountingLoop(failing = true)).also(gates::add)
        replica.gate.start()

        runBlocking { eventually(ELECTION_TIMEOUT) { replica.loop.runs.get() shouldBeGreaterThan 2 } }

        replica.meters
            .find(
                "ogcloud.controller.loop.failures",
            ).counters()
            .map { it.id.getTag("loop") } shouldContainExactly
            listOf(CountingLoop.NAME)
    }

    private fun replicas(lease: String): Pair<Replica, Replica> {
        val a = Replica(lease, "a").also(gates::add)
        val b = Replica(lease, "b").also(gates::add)
        a.gate.start()
        b.gate.start()
        return a to b
    }

    private class CountingLoop(
        private val failing: Boolean = false,
    ) : LeaderLoop {
        val runs = AtomicInteger()
        override val name = NAME
        override val interval: Duration = Duration.ofMillis(100)

        override fun runOnce() {
            runs.incrementAndGet()
            if (failing) error("simulated loop failure")
        }

        companion object {
            const val NAME = "counting"
        }
    }

    private class Replica(
        lease: String,
        identity: String,
        val loop: CountingLoop = CountingLoop(),
    ) : AutoCloseable {
        val meters = SimpleMeterRegistry()
        private val client = TestCluster.client()
        val gate =
            LeaderGate(
                client,
                ControllerProperties(NAMESPACE, identity, FAST_ELECTION.copy(leaseName = lease)),
                listOf(loop),
                meters,
            )

        override fun close() {
            gate.stop()
            client.close()
        }
    }

    private companion object {
        const val NAMESPACE = "leader-test"

        // Short timings keep the takeover test fast; production uses the defaults.
        val FAST_ELECTION =
            LeaderElectionProperties(
                leaseDuration = Duration.ofSeconds(3),
                renewDeadline = Duration.ofSeconds(2),
                retryPeriod = Duration.ofMillis(500),
            )
        val ELECTION_TIMEOUT = 20.seconds

        init {
            TestCluster.createNamespace(NAMESPACE)
        }
    }
}
