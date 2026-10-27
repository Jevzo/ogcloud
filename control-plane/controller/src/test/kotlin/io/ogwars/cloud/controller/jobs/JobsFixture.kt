package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.controller.lifecycle.PodEnd
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.network.RestartPlanner
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.persistence.JdbcJobStore
import io.ogwars.cloud.persistence.JsonCodec
import io.ogwars.cloud.persistence.TestDatabase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.UUID

/** Jobs on PostgreSQL and the lifecycle on Valkey; [settle] plays Kubernetes and the plugins between ticks. */
class JobsFixture(
    groups: List<Group>,
    maintenance: Boolean = false,
) {
    val f = LifecycleFixture(groups, maintenance = maintenance)
    val jobs = JdbcJobStore(TestDatabase.jdbc, JsonCodec())
    val progress = mutableListOf<Job>()
    val roll = RollingRestart(f.servers, f.groupStore, f.failures, f.lifecycle, { UUID.randomUUID() }, f.clock)

    init {
        // Every test sees only its own jobs.
        TestDatabase.jdbc.sql("delete from jobs").update()
    }

    fun runner(vararg handlers: JobHandler) =
        JobRunner(jobs, handlers.toList(), {
            progress += it
        }, f.clock, SimpleMeterRegistry())

    fun restartHandlers() =
        arrayOf(
            GroupRestartJob(roll, f.groupStore, RestartPlanner()),
            NetworkRestartJob(roll, f.groupStore, f.network, RestartPlanner()),
        )

    fun create(
        kind: String,
        payload: Map<String, String> = emptyMap(),
    ): UUID {
        val id = UUID.randomUUID()
        jobs.create(Job(id, kind, JobState.QUEUED, payload, emptyMap(), 0, 0, "", ADMIN, f.clock.instant()))
        return id
    }

    fun running(
        group: Group,
        players: Int = 0,
    ): UUID = f.request(group).also { start(it, players) }

    /** Requested servers come up, stopping ones go away; [failing] servers crash before they run. */
    fun settle(failing: Set<UUID> = emptySet()) {
        f.servers.listAll().map { it.value }.forEach { server ->
            when {
                server.id in failing && server.state == ServerState.REQUESTED -> {
                    f.lifecycle.podCreated(server.id)
                    f.lifecycle.podEnded(server.id, PodEnd.FAILED, "crashed")
                }

                server.state == ServerState.REQUESTED -> {
                    start(server.id, players = 0)
                }

                server.state == ServerState.STOPPING -> {
                    f.lifecycle.podEnded(server.id, PodEnd.STOPPED, "pod deleted")
                }

                else -> {}
            }
        }
    }

    /** Ticks [runner] until [job] finishes. */
    fun runToEnd(
        runner: JobRunner,
        job: UUID,
        failing: () -> Set<UUID> = { emptySet() },
    ): Job {
        repeat(MAX_TICKS) {
            runner.runOnce()
            val current = jobs.find(job) ?: error("job vanished")
            if (current.state.isFinished) return current
            settle(failing())
        }
        error("Job $job did not finish within $MAX_TICKS ticks")
    }

    private fun start(
        id: UUID,
        players: Int,
    ) {
        f.lifecycle.podCreated(id)
        f.lifecycle.podStarted(id, "10.42.0.7")
        f.lifecycle.heartbeat(id, players, maxPlayers = 50, at = f.clock.instant())
    }

    companion object {
        val ADMIN = Actor.system("test-admin")
        private const val MAX_TICKS = 60
    }
}
