package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.retention.RetentionKind
import io.ogwars.cloud.domain.runtime.RuntimeArtifact
import io.ogwars.cloud.domain.scaling.ScalingAction
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.scaling.ScalingReason
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.RetentionStore
import io.ogwars.cloud.domain.store.RuntimeArtifactStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.util.UUID

@PersistenceTest
class JobsLogsArtifactsTest(
    @Autowired private val jobs: JobStore,
    @Autowired private val audit: AuditLogStore,
    @Autowired private val scaling: ScalingLogStore,
    @Autowired private val retention: RetentionStore,
    @Autowired private val artifacts: RuntimeArtifactStore,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `jobs round-trip and only unfinished ones are active`() {
        val restart = job(JobState.QUEUED)
        val done = job(JobState.SUCCEEDED).copy(finishedAt = NOW)
        jobs.create(restart)
        jobs.create(done)

        val running =
            restart.copy(
                state = JobState.RUNNING,
                progress = mapOf("phase" to "1"),
                completedSteps = 1,
                startedAt = NOW,
            )
        jobs.update(running)

        jobs.find(restart.id) shouldBe running
        jobs.findActive() shouldContainExactly listOf(running)
    }

    @Test
    fun `retention deletes only old records, in limited batches`() {
        repeat(3) { appendAudit(NOW.minus(Duration.ofDays(100))) }
        appendAudit(NOW)
        scaling.append(
            ScalingLogEntry(
                UUID.randomUUID(),
                NOW.minus(Duration.ofDays(40)),
                GroupId("lobby"),
                ScalingAction.START,
                null,
                ScalingReason.LOAD,
            ),
        )

        retention.deleteOlderThan(RetentionKind.AUDIT_LOG, NOW.minus(Duration.ofDays(90)), limit = 2) shouldBe 2
        retention.deleteOlderThan(RetentionKind.AUDIT_LOG, NOW.minus(Duration.ofDays(90)), limit = 2) shouldBe 1
        retention.deleteOlderThan(RetentionKind.SCALING_LOG, NOW.minus(Duration.ofDays(30)), limit = 10) shouldBe 1
        jdbc.queryForObject("select count(*) from audit_log", Int::class.java) shouldBe 1
    }

    @Test
    fun `runtime artifacts are replaced per profile and path`() {
        val via =
            RuntimeArtifact(
                "velocity-4.3.0",
                "plugins/ViaVersion.jar",
                "runtime/a",
                "https://example.invalid/a",
                "b".repeat(64),
                10,
                NOW,
            )
        artifacts.save(via)
        artifacts.save(via.copy(objectKey = "runtime/b", sizeBytes = 20))

        artifacts.findByProfile("velocity-4.3.0") shouldContainExactly
            listOf(via.copy(objectKey = "runtime/b", sizeBytes = 20))
        artifacts.delete("velocity-4.3.0", "plugins/ViaVersion.jar")
        artifacts.findByProfile("velocity-4.3.0") shouldBe emptyList()
    }

    private fun job(state: JobState) =
        Job(
            id = UUID.randomUUID(),
            kind = "group-restart",
            state = state,
            payload = mapOf("groupId" to "lobby"),
            progress = emptyMap(),
            completedSteps = 0,
            totalSteps = 3,
            message = "",
            createdBy = Actor(ActorType.USER, "0192d2c1-0000-7000-8000-000000000001"),
            createdAt = NOW,
        )

    private fun appendAudit(at: java.time.Instant) =
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                at,
                Actor.system("test"),
                "group.update",
                "group",
                "lobby",
                metadata =
                    mapOf("field" to "minOnline"),
            ),
        )
}
