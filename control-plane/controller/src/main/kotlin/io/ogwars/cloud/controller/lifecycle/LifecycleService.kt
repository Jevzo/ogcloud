package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.controller.kube.PodSpecBuilder
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.DrainAction
import io.ogwars.cloud.domain.server.GracefulStopAction
import io.ogwars.cloud.domain.server.LifecyclePolicy
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.ServerTransitions
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.ServerStateStore
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** How a server's pod ended. */
enum class PodEnd {
    /** The container exited cleanly or the pod was deleted. */
    STOPPED,

    /** The container failed; before RUNNING this counts towards the crash-loop breaker. */
    FAILED,
}

/**
 * The server state machine applied to live state. Every replica may call it: compare-and-set makes a repeated or
 * concurrent call a no-op, and every real change is published once as `ServerLifecycleChanged`.
 */
@Service
class LifecycleService(
    private val servers: ServerStateStore,
    private val groups: GroupStore,
    private val pods: PodGateway,
    private val events: ServerEvents,
    private val breaker: CrashLoopBreaker,
    private val policy: LifecyclePolicy,
    private val clock: Clock,
) {
    private val updater = ServerUpdater(servers)

    /** Records a new server; the provisioner creates its pod. A repeated request returns the existing server. */
    fun request(
        id: UUID,
        groupId: GroupId,
        groupType: GroupType,
        profileId: String,
        actor: Actor,
    ): Server {
        val server = Server(id, groupId, groupType, profileId, ServerState.REQUESTED, clock.instant())
        if (!servers.create(server)) {
            return servers.get(id)?.value
                ?: error("Server $id vanished right after its creation")
        }
        events.stateChanged(ServerChange(server, server), actor, "requested")
        return server
    }

    /** The provisioner created the pod. */
    fun podCreated(id: UUID) = transition(id, SYSTEM, "pod created") { it.moveTo(ServerState.PREPARING) }

    /** The server container runs at [host]; plugins report RUNNING through their first heartbeat. */
    fun podStarted(
        id: UUID,
        host: String,
    ) = transition(id, SYSTEM, "container started") {
        it.moveTo(ServerState.STARTING)?.copy(host = host, port = PodSpecBuilder.MINECRAFT_PORT)
    }

    fun heartbeat(
        id: UUID,
        players: Int,
        maxPlayers: Int,
        at: Instant,
    ) {
        val change =
            updater.update(id) { server ->
                val alive =
                    if (server.state ==
                        ServerState.STARTING
                    ) {
                        server.transitionTo(ServerState.RUNNING, at)
                    } else {
                        server
                    }
                alive
                    .takeIf { it.state == ServerState.RUNNING || it.state == ServerState.DRAINING }
                    ?.copy(players = players, maxPlayers = maxPlayers, lastHeartbeatAt = at)
            } ?: return
        if (change.before.state != change.after.state) events.stateChanged(change, SYSTEM, "first heartbeat")
        val wasEmpty = change.before.players == 0
        val isEmpty = change.after.players == 0
        if (wasEmpty != isEmpty) pods.setSafeToEvict(change.after.podName, isEmpty)
    }

    /** Graceful: running servers drain first, pending ones stop at once. */
    fun stop(
        id: UUID,
        actor: Actor,
        reason: String,
    ) {
        val server = servers.get(id)?.value ?: return
        when (policy.gracefulStopAction(server)) {
            GracefulStopAction.DRAIN -> drain(id, actor, reason)
            GracefulStopAction.STOP_NOW -> stopNow(id, actor, reason)
            GracefulStopAction.IGNORE -> Unit
        }
    }

    fun drain(
        id: UUID,
        actor: Actor,
        reason: String,
    ) {
        val server = servers.get(id)?.value ?: return
        // A server whose group was deleted has nowhere to drain to.
        val group = groups.find(server.groupId)?.value
        when (if (group == null) DrainAction.STOP_NOW else policy.drainAction(server, group)) {
            DrainAction.STOP_NOW -> stopNow(id, actor, reason)
            DrainAction.START_DRAINING -> transition(id, actor, reason) { it.moveTo(ServerState.DRAINING) }
            DrainAction.IGNORE -> Unit
        }
    }

    fun undrain(
        id: UUID,
        actor: Actor,
        reason: String,
    ) = transition(id, actor, reason) {
        it
            .takeIf { s ->
                s.state == ServerState.DRAINING
            }?.moveTo(ServerState.RUNNING)
    }

    /** Deletes the pod with its grace period, so a STATIC server can push its template (D-0042). */
    fun stopNow(
        id: UUID,
        actor: Actor,
        reason: String,
    ) = terminate(id, actor, reason, grace = null)

    fun kill(
        id: UUID,
        actor: Actor,
        reason: String,
    ) = terminate(id, actor, reason, grace = Duration.ZERO)

    /** The pod is gone or finished: the server becomes STOPPED and leaves live state. */
    fun podEnded(
        id: UUID,
        end: PodEnd,
        reason: String,
    ) {
        val current = servers.get(id) ?: return
        val server = current.value
        val stopped =
            (
                if (server.state ==
                    ServerState.STOPPING
                ) {
                    server
                } else {
                    server.transitionTo(ServerState.STOPPING, clock.instant())
                }
            ).transitionTo(ServerState.STOPPED, clock.instant())
        if (!servers.compareAndSet(current.revision, stopped)) return podEnded(id, end, reason)
        servers.delete(stopped)
        events.stateChanged(ServerChange(server, stopped), SYSTEM, reason)
        if (end == PodEnd.FAILED && server.startedAt == null) breaker.recordFailure(server.groupId, server.id)
    }

    private fun terminate(
        id: UUID,
        actor: Actor,
        reason: String,
        grace: Duration?,
    ) {
        val change =
            transition(id, actor, reason) {
                it.takeUnless { s -> s.state == ServerState.STOPPED }?.moveTo(ServerState.STOPPING)
            }
                ?: servers
                    .get(id)
                    ?.value
                    ?.takeIf { it.state == ServerState.STOPPING }
                    ?.let { ServerChange(it, it) }
                ?: return
        pods.delete(change.after.podName, grace)
    }

    private fun transition(
        id: UUID,
        actor: Actor,
        reason: String,
        change: (Server) -> Server?,
    ): ServerChange? = updater.update(id, change)?.also { events.stateChanged(it, actor, reason) }

    private fun Server.moveTo(target: ServerState): Server? =
        if (state ==
            target
        ) {
            null
        } else {
            takeIf { ServerTransitions.isAllowed(state, target) }?.transitionTo(target, clock.instant())
        }

    private companion object {
        val SYSTEM = Actor.system("controller")
    }
}
