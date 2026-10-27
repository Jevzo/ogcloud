package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.controller.lifecycle.LifecycleService
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.ServerStateStore
import org.springframework.stereotype.Component

/** Stops a soft-deleted group's servers and reports when none are left; the cleanup loop purges it later (D-0043). */
@Component
class GroupDeleteJob(
    private val servers: ServerStateStore,
    private val lifecycle: LifecycleService,
) : JobHandler {
    override val kind = JobKinds.GROUP_DELETE

    override fun step(job: Job): JobStep {
        val id =
            job.payload[JobKinds.GROUP_ID]?.let { runCatching { GroupId(it) }.getOrNull() }
                ?: return JobStep.Failed("payload has no valid groupId")
        val remaining = servers.listByGroup(id).map { it.value }
        if (remaining.isEmpty()) return JobStep.Succeeded("group $id has no servers left")
        remaining
            .filter {
                it.state != ServerState.STOPPING
            }.forEach { lifecycle.stopNow(it.id, job.createdBy, "group deleted") }
        return JobStep.Continue(job.progress, completedSteps = 0, totalSteps = 1, "stopping ${remaining.size} servers")
    }
}
