package io.ogwars.cloud.domain.network

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.group.scaling
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class RestartPlannerTest {
    private val planner = RestartPlanner()
    private val groups =
        listOf(
            group(type = GroupType.STATIC, id = "smp", scaling = scaling(min = 1, max = 1)),
            group(id = "bedwars"),
            group(type = GroupType.PROXY, id = "proxy"),
            group(id = "lobby"),
            group(id = "arena"),
        )

    @Test
    fun `restarts proxies, then the default group, then dynamic, then static groups`() {
        val plan = planner.planNetworkRestart(groups, GroupId("lobby"), networkMaintenance = true)

        plan shouldBe
            RestartPlanResult.Planned(
                listOf(
                    RestartPhase(listOf(GroupId("proxy"))),
                    RestartPhase(listOf(GroupId("lobby"))),
                    RestartPhase(listOf(GroupId("arena"), GroupId("bedwars"))),
                    RestartPhase(listOf(GroupId("smp"))),
                ),
            )
    }

    @Test
    fun `network restarts need network maintenance, group restarts group maintenance`() {
        planner
            .planNetworkRestart(
                groups,
                GroupId("lobby"),
                networkMaintenance = false,
            ).shouldBeInstanceOf<RestartPlanResult.Rejected>()
        planner.planGroupRestart(group()).shouldBeInstanceOf<RestartPlanResult.Rejected>()
        planner.planGroupRestart(group(maintenance = true)) shouldBe
            RestartPlanResult.Planned(listOf(RestartPhase(listOf(GroupId("lobby")))))
    }
}
