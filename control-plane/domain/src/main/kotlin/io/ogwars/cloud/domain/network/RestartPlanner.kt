package io.ogwars.cloud.domain.network

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType

/** Groups restarted together; the job runner replaces their servers and waits until they are healthy. */
data class RestartPhase(
    val groupIds: List<GroupId>,
)

sealed interface RestartPlanResult {
    data class Planned(
        val phases: List<RestartPhase>,
    ) : RestartPlanResult

    data class Rejected(
        val reason: String,
    ) : RestartPlanResult
}

class RestartPlanner {
    /**
     * Proxies first, then the default group so players have somewhere to land, then the other
     * DYNAMIC groups, then STATIC groups. Requires network maintenance.
     */
    fun planNetworkRestart(
        groups: List<Group>,
        defaultGroupId: GroupId,
        networkMaintenance: Boolean,
    ): RestartPlanResult {
        if (!networkMaintenance) return RestartPlanResult.Rejected("network maintenance must be on")
        val phases =
            listOf(
                groups.filter { it.type == GroupType.PROXY },
                groups.filter { it.id == defaultGroupId },
                groups.filter { it.type == GroupType.DYNAMIC && it.id != defaultGroupId },
                groups.filter { it.type == GroupType.STATIC && it.id != defaultGroupId },
            ).filter { it.isNotEmpty() }
                .map { phase -> RestartPhase(phase.map(Group::id).sortedBy(GroupId::value)) }
        return RestartPlanResult.Planned(phases)
    }

    fun planGroupRestart(group: Group): RestartPlanResult =
        if (group.maintenance) {
            RestartPlanResult.Planned(listOf(RestartPhase(listOf(group.id))))
        } else {
            RestartPlanResult.Rejected("group ${group.id} must be in maintenance")
        }
}
