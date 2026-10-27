package io.ogwars.cloud.api.jobs

import io.ogwars.cloud.api.events.Announcements
import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.api.web.Cursors
import io.ogwars.cloud.api.web.Page
import io.ogwars.cloud.api.web.callerPermissions
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class JobView(
    val id: UUID,
    val kind: String,
    val state: JobState,
    val payload: Map<String, String>,
    val completedSteps: Int,
    val totalSteps: Int,
    val message: String,
    /** e.g. `user:0192…` */
    val createdBy: String,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

fun Job.toView() =
    JobView(
        id,
        kind,
        state,
        payload,
        completedSteps,
        totalSteps,
        message,
        createdBy.toString(),
        createdAt,
        startedAt,
        finishedAt,
    )

/** Long-running operations: the API only creates them and reports progress; the controller runs them (D-0039). */
@Service
class JobManagement(
    private val jobs: JobStore,
    private val announcements: Announcements,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun start(
        actor: Actor,
        kind: String,
        payload: Map<String, String> = emptyMap(),
    ): Job = announce(actor, create(actor, kind, payload))

    /** Stores the job without announcing it, e.g. inside a caller's transaction; [announce] it after the commit. */
    fun create(
        actor: Actor,
        kind: String,
        payload: Map<String, String> = emptyMap(),
    ): Job {
        val job = Job(ids.next(), kind, JobState.QUEUED, payload, emptyMap(), 0, 0, "", actor, clock.instant())
        jobs.create(job)
        return job
    }

    fun announce(
        actor: Actor,
        job: Job,
    ): Job {
        announcements.job(actor, job.id)
        return job
    }

    /** Callers see the jobs of areas they may read (D-0092). */
    fun get(id: UUID): Job {
        val job = jobs.find(id) ?: throw NoSuchElementException("Job $id does not exist")
        if (!visible(job)) throw AccessDeniedException("Requires ${readPermission(job.kind).scope}")
        return job
    }

    fun list(
        before: UUID?,
        limit: Int,
    ): Page<Job> {
        val page = Cursors.page(jobs.list(before, limit + 1), limit) { it.id }
        return Page(page.items.filter(::visible), page.nextCursor)
    }

    private fun visible(job: Job) = readPermission(job.kind).scope in callerPermissions()

    companion object {
        private val GROUP_KINDS = setOf(JobKinds.GROUP_RESTART, JobKinds.GROUP_DELETE)

        /** Jobs belong to the area that started them (D-0092). */
        fun readPermission(kind: String): ApiPermission =
            if (kind in
                GROUP_KINDS
            ) {
                ApiPermission.GROUPS_READ
            } else {
                ApiPermission.NETWORK_READ
            }
    }
}

@RestController
@RequestMapping("/api/v1/jobs")
class JobController(
    private val jobs: JobManagement,
) {
    @AnyCaller
    @GetMapping
    fun list(
        @RequestParam cursor: String?,
        @RequestParam limit: Int?,
    ): Page<JobView> {
        val page = jobs.list(cursor?.let(Cursors::decode), Cursors.limit(limit))
        return Page(page.items.map { it.toView() }, page.nextCursor)
    }

    @AnyCaller
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): JobView = jobs.get(id).toView()
}
