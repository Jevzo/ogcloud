package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.JobRequested
import io.ogwars.cloud.controller.jobs.JobRunner
import io.ogwars.cloud.controller.leader.LeaderGate
import io.ogwars.cloud.messaging.toUuid
import org.springframework.stereotype.Component

/** Starts the job runner now on the leader; elsewhere nothing happens and the leader's next tick picks the job up. */
fun interface JobWakeUp {
    fun wake()
}

@Component
class LeaderJobWakeUp(
    private val gate: LeaderGate,
) : JobWakeUp {
    override fun wake() = gate.runNow(JobRunner.NAME)
}

/** `ogcloud.job.requested`: the job row exists already (D-0039); this only shortens the wait. */
@Component
class JobRequestedHandler(
    private val wakeUp: JobWakeUp,
) {
    fun handle(event: JobRequested) {
        event.jobId.toUuid("job_id")
        wakeUp.wake()
    }
}
