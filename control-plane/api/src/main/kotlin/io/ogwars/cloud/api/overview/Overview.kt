package io.ogwars.cloud.api.overview

import io.ogwars.cloud.api.logs.ScalingLogView
import io.ogwars.cloud.api.logs.toView
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.overview.GroupLoad
import io.ogwars.cloud.domain.overview.LoadHistory
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.LoadHistoryStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant

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

data class LoadPointView(
    val at: Instant,
    /** Online players, or the group's players. */
    val players: Int,
    /** Running servers of the network, or of the group. */
    val running: Int,
)

data class LoadHistoryView(
    /** Seconds between two points. */
    val intervalSeconds: Long,
    /** Oldest first. */
    val points: List<LoadPointView>,
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
    private val history: LoadHistoryStore,
    private val clock: Clock,
) {
    @Requires(ApiPermission.OVERVIEW_READ)
    @GetMapping
    fun get(): OverviewView {
        val live = servers.listAll().map { it.value }
        val loads = GroupLoad.byGroup(live)
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
                    val load = loads[group.id] ?: GroupLoad.NONE
                    GroupOverview(
                        id = group.id.value,
                        type = group.type,
                        maintenance = group.maintenance,
                        crashLoopPaused = failures.isPaused(group.id),
                        running = load.running,
                        pending = load.pending,
                        players = load.players,
                        capacity = load.running * group.scaling.playersPerServer,
                    )
                },
            recentScaling = scaling.list(groupId = null, before = null, limit = RECENT_SCALING).map { it.toView() },
        )
    }

    /** The last 24 hours a minute apart (D-0213): the network's online players, or one group's, with running servers. */
    @Requires(ApiPermission.OVERVIEW_READ)
    @GetMapping("/history")
    fun history(
        @RequestParam groupId: String?,
    ): LoadHistoryView {
        val group = groupId?.let(::GroupId)
        if (group != null && groups.find(group) == null) throw NoSuchElementException("Group $group does not exist")
        val samples = history.since(clock.instant().minus(LoadHistory.KEPT))
        val points =
            if (group == null) {
                samples.map { LoadPointView(it.at, it.players, it.groups.values.sumOf { load -> load.running }) }
            } else {
                // A sample without the group predates it, so the series starts when the group was created.
                samples.mapNotNull { sample ->
                    sample.groups[group]?.let { LoadPointView(sample.at, it.players, it.running) }
                }
            }
        return LoadHistoryView(LoadHistory.INTERVAL.toSeconds(), points)
    }

    private companion object {
        const val RECENT_SCALING = 10
    }
}
