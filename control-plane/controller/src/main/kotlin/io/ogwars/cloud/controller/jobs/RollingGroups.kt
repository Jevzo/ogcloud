package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.store.JobStore
import org.springframework.stereotype.Component

/** The groups whose servers running jobs are replacing, which the autoscaler leaves undrained meanwhile (D-0242). */
@Component
class RollingGroups(
    private val jobs: JobStore,
    handlers: List<JobHandler>,
) {
    private val handlers = handlers.associateBy { it.kind }

    fun current(): Set<GroupId> =
        jobs
            .findActive()
            .filter { it.state == JobState.RUNNING }
            .flatMapTo(mutableSetOf()) { handlers[it.kind]?.rolledGroups(it).orEmpty() }
}
