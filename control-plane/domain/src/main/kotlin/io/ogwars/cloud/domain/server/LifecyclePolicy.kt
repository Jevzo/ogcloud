package io.ogwars.cloud.domain.server

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import java.time.Duration
import java.time.Instant

/** How long a server may stay in each state before the controller intervenes. */
data class LifecycleTimeouts(
    val requested: Duration = Duration.ofSeconds(60),
    val preparing: Duration = Duration.ofMinutes(3),
    val starting: Duration = Duration.ofSeconds(90),
    val heartbeat: Duration = Duration.ofSeconds(30),
    val stopping: Duration = Duration.ofMinutes(3),
    /** After a drain timeout, players get this long to be transferred before a forced stop. */
    val transferGrace: Duration = Duration.ofSeconds(30),
)

/** What the controller does with a server whose deadline passed. */
sealed interface OverdueAction {
    data object None : OverdueAction

    /** Never became healthy: delete the pod and forget the server. */
    data class CleanUp(
        val reason: String,
    ) : OverdueAction

    data class Stop(
        val reason: String,
    ) : OverdueAction

    data class TransferPlayers(
        val target: TransferTarget,
    ) : OverdueAction
}

/** Where players go when their server stops; [Kick] disconnects them. */
sealed interface TransferTarget {
    data class ToGroup(
        val groupId: GroupId,
    ) : TransferTarget

    data object Kick : TransferTarget
}

class LifecyclePolicy(
    private val timeouts: LifecycleTimeouts = LifecycleTimeouts(),
) {
    /** Checked on every sweep; every state except STOPPED has a deadline. */
    fun overdueAction(
        server: Server,
        group: Group,
        defaultGroupId: GroupId,
        now: Instant,
    ): OverdueAction {
        val inState = Duration.between(server.stateSince, now)
        return when (server.state) {
            ServerState.REQUESTED -> {
                cleanUpAfter(inState, timeouts.requested, "stuck in REQUESTED")
            }

            ServerState.PREPARING -> {
                cleanUpAfter(inState, timeouts.preparing, "stuck in PREPARING")
            }

            ServerState.STARTING -> {
                cleanUpAfter(inState, timeouts.starting, "no heartbeat after start")
            }

            ServerState.RUNNING -> {
                heartbeatCheck(server, now)
            }

            // A draining server that stops heartbeating would otherwise never report an empty count.
            ServerState.DRAINING -> {
                heartbeatCheck(server, now).takeUnless { it == OverdueAction.None }
                    ?: drainCheck(server, group, defaultGroupId, inState)
            }

            ServerState.STOPPING -> {
                cleanUpAfter(inState, timeouts.stopping, "pod did not terminate")
            }

            ServerState.STOPPED -> {
                OverdueAction.None
            }
        }
    }

    /** v1 behaviour: an empty server, or a group without drain timeout, stops at once. */
    fun drainAction(
        server: Server,
        group: Group,
    ): DrainAction =
        when {
            server.state != ServerState.RUNNING -> DrainAction.IGNORE
            server.players == 0 || group.drainTimeout.isZero -> DrainAction.STOP_NOW
            else -> DrainAction.START_DRAINING
        }

    /** A graceful stop drains running servers and stops ones that never got players. */
    fun gracefulStopAction(server: Server): GracefulStopAction =
        when {
            server.state == ServerState.RUNNING -> GracefulStopAction.DRAIN
            server.state.isPending -> GracefulStopAction.STOP_NOW
            else -> GracefulStopAction.IGNORE
        }

    fun gameStateAction(
        server: Server,
        newState: GameState,
    ): GameStateAction =
        when {
            server.groupType != GroupType.DYNAMIC || server.state != ServerState.RUNNING -> GameStateAction.IGNORE
            newState == GameState.ENDING -> GameStateAction.END_GAME
            else -> GameStateAction.UPDATE
        }

    fun transferTarget(
        server: Server,
        defaultGroupId: GroupId,
    ): TransferTarget =
        when (server.groupType) {
            GroupType.PROXY -> TransferTarget.Kick
            GroupType.DYNAMIC, GroupType.STATIC -> TransferTarget.ToGroup(defaultGroupId)
        }

    private fun cleanUpAfter(
        inState: Duration,
        limit: Duration,
        reason: String,
    ): OverdueAction = if (inState > limit) OverdueAction.CleanUp(reason) else OverdueAction.None

    private fun heartbeatCheck(
        server: Server,
        now: Instant,
    ): OverdueAction {
        val lastSign = server.lastHeartbeatAt ?: server.stateSince
        return if (Duration.between(lastSign, now) > timeouts.heartbeat) {
            OverdueAction.Stop("no heartbeat for ${timeouts.heartbeat.seconds}s")
        } else {
            OverdueAction.None
        }
    }

    private fun drainCheck(
        server: Server,
        group: Group,
        defaultGroupId: GroupId,
        inState: Duration,
    ): OverdueAction =
        when {
            server.players == 0 -> {
                OverdueAction.Stop("drained")
            }

            // Proxies only stop once empty; kicking a whole proxy is never automatic.
            server.groupType == GroupType.PROXY -> {
                OverdueAction.None
            }

            inState >= group.drainTimeout + timeouts.transferGrace -> {
                OverdueAction.Stop(
                    "drain timeout, transfer grace expired",
                )
            }

            inState >= group.drainTimeout -> {
                OverdueAction.TransferPlayers(transferTarget(server, defaultGroupId))
            }

            else -> {
                OverdueAction.None
            }
        }
}

enum class DrainAction { IGNORE, STOP_NOW, START_DRAINING }

enum class GracefulStopAction { IGNORE, DRAIN, STOP_NOW }

enum class GameStateAction { IGNORE, UPDATE, END_GAME }
