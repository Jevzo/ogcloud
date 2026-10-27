package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.LifecyclePolicy
import io.ogwars.cloud.domain.server.OverdueAction
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Every state has a deadline: stuck servers are cleaned up, silent ones stopped, drained ones moved on. */
@Component
class DeadlineSweeper(
    private val servers: ServerStateStore,
    private val groups: GroupStore,
    private val network: NetworkSettingsStore,
    private val policy: LifecyclePolicy,
    private val lifecycle: LifecycleService,
    private val pods: PodGateway,
    private val events: ServerEvents,
    private val properties: LifecycleProperties,
    private val clock: Clock,
    private val meters: MeterRegistry,
) : LeaderLoop {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Servers whose players were already sent away, so the transfer is requested once per drain. */
    private val transferred: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    override val name = "deadline-sweeper"
    override val interval: Duration get() = properties.sweepInterval

    override fun runOnce() {
        val defaultGroupId = network.load().value.defaultGroupId
        val now = clock.instant()
        val current = servers.listAll().map { it.value }
        transferred.retainAll(current.mapTo(HashSet()) { it.id })
        val heartbeatsFlowing = heartbeatsFlowing(current, now)
        val groupCache = mutableMapOf<GroupId, Group?>()
        current.forEach { server ->
            val group = groupCache.getOrPut(server.groupId) { groups.find(server.groupId)?.value }
            if (group == null) {
                withoutGroup(server, now)
                return@forEach
            }
            when (val action = policy.overdueAction(server, group, defaultGroupId, now)) {
                is OverdueAction.CleanUp -> {
                    cleanUp(server, action.reason)
                }

                is OverdueAction.Stop -> {
                    if (heartbeatsFlowing ||
                        !action.missedHeartbeats
                    ) {
                        lifecycle.stopNow(server.id, SYSTEM, action.reason)
                    }
                }

                is OverdueAction.TransferPlayers -> {
                    transferOnce(server, action)
                }

                OverdueAction.None -> {}
            }
        }
    }

    /**
     * When none of several running servers heartbeated within the deadline, the heartbeat pipeline (Kafka, the
     * consumers) is stalled rather than every server dead, and stopping them all would take the network down.
     * A single silent server is stopped: crashed pods are caught by the pod watcher anyway.
     */
    private fun heartbeatsFlowing(
        current: List<Server>,
        now: Instant,
    ): Boolean {
        val running = current.filter { it.state == ServerState.RUNNING || it.state == ServerState.DRAINING }
        if (running.size < MIN_FLEET_FOR_STALL_CHECK) return true
        val freshest = running.mapNotNull { it.lastHeartbeatAt }.maxOrNull()
        val flowing = freshest != null && Duration.between(freshest, now) <= properties.timeouts.heartbeat
        if (!flowing) {
            log.warn(
                "None of {} running servers heartbeated within {}; holding back heartbeat stops",
                running.size,
                properties.timeouts.heartbeat,
            )
            Counter.builder(STALLED_METRIC).register(meters).increment()
        }
        return flowing
    }

    /** A deleted group's servers stop; one whose pod will not go is forced away after the STOPPING deadline. */
    private fun withoutGroup(
        server: Server,
        now: Instant,
    ) {
        when {
            server.state != ServerState.STOPPING -> {
                lifecycle.stopNow(
                    server.id,
                    SYSTEM,
                    "group ${server.groupId} no longer exists",
                )
            }

            Duration.between(
                server.stateSince,
                now,
            ) > properties.timeouts.stopping -> {
                cleanUp(server, "pod did not terminate")
            }
        }
    }

    // Marked only after the publish succeeded, so a failed one is retried next sweep.
    private fun transferOnce(
        server: Server,
        action: OverdueAction.TransferPlayers,
    ) {
        if (server.id in transferred) return
        events.transferPlayers(server.id, action.target, "drain timeout")
        transferred.add(server.id)
    }

    /** A server that never became healthy, or whose pod will not terminate: force the pod away and forget it. */
    private fun cleanUp(
        server: Server,
        reason: String,
    ) {
        pods.delete(server.podName, Duration.ZERO)
        val failedStart = server.state == ServerState.PREPARING || server.state == ServerState.STARTING
        lifecycle.podEnded(server.id, if (failedStart) PodEnd.FAILED else PodEnd.STOPPED, reason)
    }

    private companion object {
        val SYSTEM = Actor.system("controller")
        const val STALLED_METRIC = "ogcloud.controller.heartbeats.stalled"
        const val MIN_FLEET_FOR_STALL_CHECK = 2
    }
}
