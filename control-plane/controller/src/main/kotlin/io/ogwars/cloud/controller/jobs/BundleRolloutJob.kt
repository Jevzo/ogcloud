package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.store.GroupStore
import org.springframework.stereotype.Component

/** Replaces the servers of a profile whose bundle changed; servers drain first, so players are not kicked. */
@Component
class BundleRolloutJob(
    private val groups: GroupStore,
    private val roll: RollingRestart,
) : JobHandler {
    override val kind = JobKinds.BUNDLE_ROLLOUT

    override fun step(job: Job): JobStep {
        val profileId = job.payload[JobKinds.PROFILE_ID] ?: return JobStep.Failed("payload has no profileId")
        val groupIds =
            groups
                .findAll()
                .map {
                    it.value
                }.filter { it.profileId == profileId }
                .map { it.id }
                .sortedBy { it.value }
        val options =
            RollOptions(
                job.createdBy,
                job.payload[JobKinds.BATCH_SIZE]?.toIntOrNull() ?: DEFAULT_BATCH_SIZE,
                graceful = true,
            )
        return when (val result = roll.step(listOf(groupIds), job.progress, options)) {
            is RollResult.Running -> JobStep.Continue(result.state, result.completedPhases, 1, result.message)
            RollResult.Finished -> JobStep.Succeeded("servers of $profileId run the new bundle")
            is RollResult.Failed -> JobStep.Failed(result.reason)
        }
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 5
    }
}
