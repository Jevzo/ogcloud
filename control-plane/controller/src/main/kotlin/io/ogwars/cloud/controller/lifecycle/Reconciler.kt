package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.controller.kube.PodObservation
import io.ogwars.cloud.controller.kube.PodRecovery
import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import io.fabric8.kubernetes.api.model.Pod
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Repairs drift between live state and Kubernetes. It runs as soon as a replica becomes leader and then
 * periodically: orphan pods are adopted or deleted, servers whose pod vanished are ended, and sessions on
 * a vanished proxy are dropped.
 */
@Component
class Reconciler(
    private val servers: ServerStateStore,
    private val sessions: SessionStore,
    private val groups: GroupStore,
    private val pods: PodGateway,
    private val lifecycle: LifecycleService,
    private val properties: LifecycleProperties,
    private val clock: Clock,
    private val meters: MeterRegistry,
) : LeaderLoop {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name = "reconciler"
    override val interval: Duration get() = properties.reconcileInterval

    override fun runOnce() {
        // Records first, then pods: the provisioner creates a pod before it moves the server past REQUESTED.
        val records = servers.listAll().map { it.value }
        val livePods = pods.list().filter { it.metadata.deletionTimestamp == null }
        val recordIds = records.mapTo(HashSet()) { it.id }
        val adopted = livePods.filter { PodObservation.serverId(it) !in recordIds }.mapNotNull(::adoptOrDelete)
        val podNames = livePods.mapTo(HashSet()) { it.metadata.name }
        val ended = records.filter { it.expectsPod() && it.podName !in podNames }.onEach(::endWithoutPod).map { it.id }
        dropStaleSessions(recordIds - ended.toSet() + adopted)
    }

    private fun adoptOrDelete(pod: Pod): UUID? {
        val server = PodRecovery.serverFrom(pod, clock.instant())?.takeIf(::restorable)
        if (server == null) {
            log.warn("Deleting orphan pod {}: it cannot be matched to a group and profile", pod.metadata.name)
            pods.delete(pod.metadata.name, Duration.ZERO)
            repaired(ORPHAN_DELETED)
            return null
        }
        if (!lifecycle.restore(server)) return null
        log.info("Restored server {} from pod {}", server.id, pod.metadata.name)
        repaired(RESTORED)
        return server.id
    }

    private fun restorable(server: Server) =
        groups.find(server.groupId) != null && RuntimeProfile.find(server.profileId) != null

    private fun endWithoutPod(server: Server) {
        log.warn("Server {} has no pod; ending it", server.id)
        lifecycle.podEnded(server.id, PodEnd.STOPPED, "pod missing")
        repaired(MISSING_POD)
    }

    private fun dropStaleSessions(liveServers: Set<UUID>) {
        sessions.onlinePlayers().forEach { player ->
            val session = sessions.get(player) ?: return@forEach
            if (session.value.proxyId !in liveServers && sessions.remove(session)) repaired(STALE_SESSION)
        }
    }

    private fun Server.expectsPod() = state != ServerState.REQUESTED && state != ServerState.STOPPED

    private fun repaired(kind: String) {
        Counter
            .builder(REPAIRS_METRIC)
            .tag("kind", kind)
            .register(meters)
            .increment()
    }

    private companion object {
        const val REPAIRS_METRIC = "ogcloud.controller.reconciler.repairs"
        const val RESTORED = "restored"
        const val ORPHAN_DELETED = "orphan-deleted"
        const val MISSING_POD = "missing-pod"
        const val STALE_SESSION = "stale-session"
    }
}
