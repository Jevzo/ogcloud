package io.ogwars.cloud.controller.jobs

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.network.RestartPlanResult
import io.ogwars.cloud.domain.network.RestartPlanner
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import org.springframework.stereotype.Component

/** Shared by group and network restarts: plan once, store the phases, then roll. */
abstract class RestartJob(
    private val roll: RollingRestart,
) : JobHandler {
    protected abstract fun plan(job: Job): RestartPlanResult

    override fun step(job: Job): JobStep {
        val phases =
            job.progress[PHASES]?.let(::decodePhases) ?: when (val plan = plan(job)) {
                is RestartPlanResult.Rejected -> return JobStep.Failed(plan.reason)
                is RestartPlanResult.Planned -> plan.phases.map { it.groupIds }
            }
        val options =
            RollOptions(
                job.createdBy,
                job.payload[JobKinds.BATCH_SIZE]?.toIntOrNull() ?: JobKinds.DEFAULT_BATCH_SIZE,
                graceful = false,
            )
        return when (val result = roll.step(phases, job.progress - PHASES, options)) {
            is RollResult.Running -> {
                JobStep.Continue(
                    result.state + (PHASES to encodePhases(phases)),
                    result.completedPhases,
                    phases.size,
                    result.message,
                )
            }

            RollResult.Finished -> {
                JobStep.Succeeded("restarted ${phases.size} phases")
            }

            is RollResult.Failed -> {
                JobStep.Failed(result.reason)
            }
        }
    }

    private companion object {
        const val PHASES = "phases"

        fun encodePhases(phases: List<List<GroupId>>) =
            phases.joinToString("|") { phase ->
                phase.joinToString(",") {
                    it.value
                }
            }

        fun decodePhases(text: String) =
            text.split('|').filter(String::isNotEmpty).map { phase -> phase.split(',').map(::GroupId) }
    }
}

@Component
class GroupRestartJob(
    roll: RollingRestart,
    private val groups: GroupStore,
    private val planner: RestartPlanner,
) : RestartJob(roll) {
    override val kind = JobKinds.GROUP_RESTART

    override fun plan(job: Job): RestartPlanResult {
        val id =
            job.payload[JobKinds.GROUP_ID]?.let { runCatching { GroupId(it) }.getOrNull() }
                ?: return RestartPlanResult.Rejected("payload has no valid groupId")
        val group = groups.find(id)?.value ?: return RestartPlanResult.Rejected("group $id does not exist")
        return planner.planGroupRestart(group)
    }
}

@Component
class NetworkRestartJob(
    roll: RollingRestart,
    private val groups: GroupStore,
    private val network: NetworkSettingsStore,
    private val planner: RestartPlanner,
) : RestartJob(roll) {
    override val kind = JobKinds.NETWORK_RESTART

    override fun plan(job: Job): RestartPlanResult {
        val settings = network.load().value
        return planner.planNetworkRestart(
            groups.findAll().map { it.value },
            settings.defaultGroupId,
            settings.maintenance,
        )
    }
}
