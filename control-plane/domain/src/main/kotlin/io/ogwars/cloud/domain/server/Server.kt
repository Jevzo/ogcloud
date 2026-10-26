package io.ogwars.cloud.domain.server

import io.ogwars.cloud.domain.common.compactHex
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import java.time.Instant
import java.util.UUID

enum class ServerState {
    REQUESTED,
    PREPARING,
    STARTING,
    RUNNING,
    DRAINING,
    STOPPING,
    STOPPED,
    ;

    /** Not yet able to take players, but counted as capacity on its way. */
    val isPending: Boolean get() = this == REQUESTED || this == PREPARING || this == STARTING
}

/** Only DYNAMIC servers have a game state. */
enum class GameState {
    LOBBY,
    INGAME,

    /** The game is over: players are moved away and the server drains. */
    ENDING,
}

data class Server(
    val id: UUID,
    val groupId: GroupId,
    val groupType: GroupType,
    val state: ServerState,
    /** When the current [state] was entered; deadlines count from here. */
    val stateSince: Instant,
    val players: Int = 0,
    val maxPlayers: Int = 0,
    val gameState: GameState? = if (groupType == GroupType.DYNAMIC) GameState.LOBBY else null,
    val startedAt: Instant? = null,
    val lastHeartbeatAt: Instant? = null,
) {
    /** Pod and proxy registration name; uses the full id, so group names may contain dashes. */
    val podName: String get() = "$groupId-${id.compactHex()}"

    /** Short name shown to players and in the dashboard. */
    val displayName: String get() = "$groupId-${id.compactHex().take(DISPLAY_ID_LENGTH)}"

    /** Moves to [target], or fails when the transition table does not allow it. */
    fun transitionTo(
        target: ServerState,
        at: Instant,
    ): Server {
        check(ServerTransitions.isAllowed(state, target)) { "Server $id cannot move from $state to $target" }
        return copy(
            state = target,
            stateSince = at,
            startedAt = if (target == ServerState.STARTING) at else startedAt,
        )
    }

    private companion object {
        const val DISPLAY_ID_LENGTH = 6
    }
}

object ServerTransitions {
    private val allowed: Map<ServerState, Set<ServerState>> =
        mapOf(
            ServerState.REQUESTED to setOf(ServerState.PREPARING, ServerState.STOPPING),
            ServerState.PREPARING to setOf(ServerState.STARTING, ServerState.STOPPING),
            ServerState.STARTING to setOf(ServerState.RUNNING, ServerState.STOPPING),
            ServerState.RUNNING to setOf(ServerState.DRAINING, ServerState.STOPPING),
            ServerState.DRAINING to setOf(ServerState.RUNNING, ServerState.STOPPING),
            ServerState.STOPPING to setOf(ServerState.STOPPED),
            ServerState.STOPPED to emptySet(),
        )

    fun isAllowed(
        from: ServerState,
        to: ServerState,
    ): Boolean = to in allowed.getValue(from)
}
