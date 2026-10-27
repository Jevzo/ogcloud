package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JdbcJobStore(
    private val jdbc: JdbcClient,
    private val json: JsonCodec,
) : JobStore {
    private val jobMapper =
        RowMapper { rs, _ ->
            Job(
                id = rs.uuid("id"),
                kind = rs.getString("kind"),
                state = JobState.valueOf(rs.getString("state")),
                payload = json.readStringMap(rs.getString("payload")),
                progress = json.readStringMap(rs.getString("progress")),
                completedSteps = rs.getInt("completed_steps"),
                totalSteps = rs.getInt("total_steps"),
                message = rs.getString("message"),
                createdBy = Actor.parse(rs.getString("created_by")),
                createdAt = rs.instant("created_at"),
                startedAt = rs.instantOrNull("started_at"),
                finishedAt = rs.instantOrNull("finished_at"),
                version = rs.getLong("version"),
            )
        }

    override fun create(job: Job) {
        jdbc
            .sql(
                """
                insert into jobs (id, kind, state, payload, progress, completed_steps, total_steps, message,
                    created_by, created_at, started_at, finished_at)
                values (:id, :kind, :state, cast(:payload as jsonb), cast(:progress as jsonb), :completed, :total,
                    :message, :createdBy, :createdAt, :startedAt, :finishedAt)
                """.trimIndent(),
            ).params(parameters(job))
            .update()
    }

    override fun find(id: UUID): Job? =
        jdbc
            .sql("select * from jobs where id = :id")
            .param("id", id)
            .query(jobMapper)
            .optional()
            .orElse(null)

    override fun list(
        before: UUID?,
        limit: Int,
    ): List<Job> =
        jdbc
            .sql("select * from jobs where id < :before order by id desc limit :limit")
            .param("before", before ?: MAX_ID)
            .param("limit", limit)
            .query(jobMapper)
            .list()

    override fun update(job: Job): Job {
        val updated =
            jdbc
                .sql(
                    """
                    update jobs set state = :state, payload = cast(:payload as jsonb), progress = cast(:progress as jsonb),
                        completed_steps = :completed, total_steps = :total, message = :message,
                        started_at = :startedAt, finished_at = :finishedAt, version = version + 1
                    where id = :id and version = :version
                    """.trimIndent(),
                ).params(parameters(job) + ("version" to job.version))
                .update()
        if (updated == 0) {
            if (find(job.id) == null) throw NoSuchElementException("Job ${job.id} does not exist")
            throw StaleUpdateException("Job ${job.id} changed since version ${job.version}")
        }
        return job.copy(version = job.version + 1)
    }

    override fun findActive(): List<Job> =
        jdbc
            .sql("select * from jobs where state in ('QUEUED', 'RUNNING') order by created_at")
            .query(jobMapper)
            .list()

    private fun parameters(job: Job): Map<String, Any?> =
        mapOf(
            "id" to job.id,
            "kind" to job.kind,
            "state" to job.state.name,
            "payload" to json.write(job.payload),
            "progress" to json.write(job.progress),
            "completed" to job.completedSteps,
            "total" to job.totalSteps,
            "message" to job.message,
            "createdBy" to job.createdBy.toString(),
            "createdAt" to job.createdAt.toTimestamp(),
            "startedAt" to job.startedAt?.toTimestamp(),
            "finishedAt" to job.finishedAt?.toTimestamp(),
        )
}
