package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * Advances every active job by one step per tick on the leader. Each write is fenced by the job's version, so a
 * deposed leader cannot overwrite the new leader's progress (D-0069).
 */
@Component
class JobRunner(
    private val jobs: JobStore,
    handlers: List<JobHandler>,
    private val events: JobEvents,
    private val clock: Clock,
    private val meters: MeterRegistry,
) : LeaderLoop {
    private val log = LoggerFactory.getLogger(javaClass)
    private val handlers = handlers.associateBy { it.kind }

    override val name = NAME
    override val interval: Duration = Duration.ofSeconds(INTERVAL_SECONDS)

    override fun runOnce() {
        val active = jobs.findActive()
        var exclusiveRunning = active.any { it.kind in JobKinds.EXCLUSIVE && it.state == JobState.RUNNING }
        active.forEach { job ->
            if (job.kind in JobKinds.EXCLUSIVE && job.state == JobState.QUEUED) {
                if (exclusiveRunning) return@forEach
                exclusiveRunning = true
            }
            advanceGuarded(job)
        }
    }

    private fun advanceGuarded(job: Job) {
        try {
            advance(job)
        } catch (e: StaleUpdateException) {
            log.warn("Job {} was changed by another leader; leaving it", job.id, e)
        } catch (e: Exception) {
            // Transient failures (a store briefly down) are retried next tick; handlers fail jobs that cannot succeed.
            log.error("Step of job {} ({}) failed; retrying", job.id, job.kind, e)
            Counter
                .builder(FAILURES_METRIC)
                .tag("kind", job.kind)
                .register(meters)
                .increment()
        }
    }

    private fun advance(queued: Job) {
        val now = clock.instant()
        val job =
            if (queued.state ==
                JobState.QUEUED
            ) {
                save(queued, queued.copy(state = JobState.RUNNING, startedAt = now, message = "started"))
            } else {
                queued
            }
        val handler = handlers[job.kind]
        val step = handler?.step(job) ?: JobStep.Failed("Unknown job kind ${job.kind}")
        val next =
            when (step) {
                is JobStep.Continue -> {
                    job.copy(
                        progress = step.progress,
                        completedSteps = step.completedSteps,
                        totalSteps = step.totalSteps,
                        message = step.message,
                    )
                }

                is JobStep.Succeeded -> {
                    job.copy(
                        state = JobState.SUCCEEDED,
                        finishedAt = now,
                        completedSteps = job.totalSteps,
                        message = step.message,
                    )
                }

                is JobStep.Failed -> {
                    job.copy(state = JobState.FAILED, finishedAt = now, message = step.message)
                }
            }
        if (next != job) save(job, next)
    }

    private fun save(
        before: Job,
        after: Job,
    ): Job {
        val saved = jobs.update(after)
        if (before.state != after.state || before.completedSteps != after.completedSteps ||
            before.message != after.message
        ) {
            events.progress(saved)
        }
        if (after.state.isFinished) log.info("Job {} ({}) {}: {}", after.id, after.kind, after.state, after.message)
        return saved
    }

    companion object {
        const val NAME = "job-runner"
        private const val INTERVAL_SECONDS = 2L
        private const val FAILURES_METRIC = "ogcloud.controller.job.step.failures"
    }
}
