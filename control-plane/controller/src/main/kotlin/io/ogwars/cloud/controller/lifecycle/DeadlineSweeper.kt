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
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
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
) : LeaderLoop {
    /** Servers whose players were already sent away, so the transfer is requested once per drain. */
    private val transferred: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    override val name = "deadline-sweeper"
    override val interval: Duration get() = properties.sweepInterval

    override fun runOnce() {
        val defaultGroupId = network.load().value.defaultGroupId
        val now = clock.instant()
        val current = servers.listAll().map { it.value }
        transferred.retainAll(current.mapTo(HashSet()) { it.id })
        val groupCache = mutableMapOf<GroupId, Group?>()
        current.forEach { server ->
            val group = groupCache.getOrPut(server.groupId) { groups.find(server.groupId)?.value }
            if (group == null) {
                if (server.state !=
                    ServerState.STOPPING
                ) {
                    lifecycle.stopNow(server.id, SYSTEM, "group ${server.groupId} no longer exists")
                }
                return@forEach
            }
            when (val action = policy.overdueAction(server, group, defaultGroupId, now)) {
                is OverdueAction.CleanUp -> {
                    cleanUp(server, action.reason)
                }

                is OverdueAction.Stop -> {
                    lifecycle.stopNow(server.id, SYSTEM, action.reason)
                }

                is OverdueAction.TransferPlayers -> {
                    transferOnce(server, action)
                }

                OverdueAction.None -> {}
            }
        }
    }

    private fun transferOnce(
        server: Server,
        action: OverdueAction.TransferPlayers,
    ) {
        if (transferred.add(server.id)) events.transferPlayers(server.id, action.target, "drain timeout")
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
    }
}
