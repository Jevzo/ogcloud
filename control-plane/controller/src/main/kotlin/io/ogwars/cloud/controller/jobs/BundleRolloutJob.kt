package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.group.GroupId
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
        val groupIds = groupsOf(profileId)
        val options =
            RollOptions(
                job.createdBy,
                job.payload[JobKinds.BATCH_SIZE]?.toIntOrNull() ?: JobKinds.DEFAULT_BATCH_SIZE,
                graceful = true,
            )
        return when (val result = roll.step(listOf(groupIds), job.progress, options)) {
            is RollResult.Running -> JobStep.Continue(result.state, result.completedPhases, 1, result.message)
            RollResult.Finished -> JobStep.Succeeded("servers of $profileId run the new bundle")
            is RollResult.Failed -> JobStep.Failed(result.reason)
        }
    }

    override fun rolledGroups(job: Job): Set<GroupId> =
        job.payload[JobKinds.PROFILE_ID]
            ?.let(::groupsOf)
            .orEmpty()
            .toSet()

    private fun groupsOf(profileId: String): List<GroupId> =
        groups
            .findAll()
            .map {
                it.value
            }.filter { it.profileId == profileId }
            .map { it.id }
            .sortedBy { it.value }
}
