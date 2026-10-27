package io.ogwars.cloud.api.overview

import io.ogwars.cloud.api.logs.ScalingLogView
import io.ogwars.cloud.api.logs.toView
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class GroupOverview(
    val id: String,
    val type: GroupType,
    val maintenance: Boolean,
    /** Latched by the crash-loop breaker until resumed. */
    val crashLoopPaused: Boolean,
    val running: Int,
    /** Requested, preparing or starting. */
    val pending: Int,
    val players: Int,
    /** Players the running servers take at most. */
    val capacity: Int,
)

data class OverviewView(
    val playersOnline: Int,
    val maxPlayers: Int,
    val maintenance: Boolean,
    val eulaAccepted: Boolean,
    val serversByState: Map<ServerState, Int>,
    val groups: List<GroupOverview>,
    val recentScaling: List<ScalingLogView>,
)

/** The dashboard's first page (D-0104). */
@RestController
@RequestMapping("/api/v1/overview")
class OverviewController(
    private val groups: GroupStore,
    private val servers: ServerStateStore,
    private val sessions: SessionStore,
    private val network: NetworkSettingsStore,
    private val failures: CrashFailureStore,
    private val scaling: ScalingLogStore,
) {
    @Requires(ApiPermission.OVERVIEW_READ)
    @GetMapping
    fun get(): OverviewView {
        val live = servers.listAll().map { it.value }
        val byGroup = live.groupBy { it.groupId }
        val settings = network.load().value
        return OverviewView(
            playersOnline = sessions.onlinePlayers().size,
            maxPlayers = settings.maxPlayers,
            maintenance = settings.maintenance,
            eulaAccepted = settings.eulaAccepted,
            serversByState = live.groupingBy { it.state }.eachCount(),
            groups =
                groups.findAll().map { revisioned ->
                    val group = revisioned.value
                    val members = byGroup[group.id].orEmpty()
                    val running = members.count { it.state == ServerState.RUNNING }
                    GroupOverview(
                        id = group.id.value,
                        type = group.type,
                        maintenance = group.maintenance,
                        crashLoopPaused = failures.isPaused(group.id),
                        running = running,
                        pending = members.count { it.state.isPending },
                        players = members.sumOf { it.players },
                        capacity = running * group.scaling.playersPerServer,
                    )
                },
            recentScaling = scaling.list(groupId = null, before = null, limit = RECENT_SCALING).map { it.toView() },
        )
    }

    private companion object {
        const val RECENT_SCALING = 10
    }
}
