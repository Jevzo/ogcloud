package io.ogwars.cloud.domain.overview

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import java.time.Duration
import java.time.Instant

/** A group's servers and players at one moment, counted alike by the overview and its history (D-0213). */
data class GroupLoad(
    val running: Int,
    /** Requested, preparing or starting. */
    val pending: Int,
    /** Players on the group's servers, from their heartbeats. */
    val players: Int,
) {
    companion object {
        val NONE = GroupLoad(running = 0, pending = 0, players = 0)

        fun byGroup(servers: Collection<Server>): Map<GroupId, GroupLoad> =
            servers.groupBy { it.groupId }.mapValues { (_, members) ->
                GroupLoad(
                    running = members.count { it.state == ServerState.RUNNING },
                    pending = members.count { it.state.isPending },
                    players = members.sumOf { it.players },
                )
            }
    }
}

/** The network's load at [at]: online players and every group that existed then, idle ones included. */
data class LoadSample(
    val at: Instant,
    val players: Int,
    val groups: Map<GroupId, GroupLoad>,
) {
    companion object {
        fun of(
            at: Instant,
            onlinePlayers: Int,
            groupIds: Collection<GroupId>,
            servers: Collection<Server>,
        ): LoadSample {
            val loads = GroupLoad.byGroup(servers)
            return LoadSample(at, onlinePlayers, groupIds.associateWith { loads[it] ?: GroupLoad.NONE })
        }
    }
}

/** How often the controller samples the load and how long the samples are kept (D-0213, D-0043). */
object LoadHistory {
    val INTERVAL: Duration = Duration.ofMinutes(1)
    val KEPT: Duration = Duration.ofHours(24)
}
