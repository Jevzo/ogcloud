package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.controller.kube.PodSpecBuilder
import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.crashloop.CrashLoopStatus
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.ServerStateStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Creates pods for REQUESTED servers, oldest first, within the rate limit and PREPARING cap (D-0075). */
@Component
class Provisioner(
    private val servers: ServerStateStore,
    private val groups: GroupStore,
    private val network: NetworkSettingsStore,
    private val breaker: CrashLoopBreaker,
    private val builder: PodSpecBuilder,
    private val pods: PodGateway,
    private val lifecycle: LifecycleService,
    private val properties: LifecycleProperties,
) : LeaderLoop {
    private val log = LoggerFactory.getLogger(javaClass)
    private val waitingForEula = AtomicBoolean(false)

    override val name = "provisioner"
    override val interval: Duration get() = properties.provisioning.interval

    override fun runOnce() {
        val settings = network.load().value
        if (!eulaAccepted(settings)) return
        val all = servers.listAll().map { it.value }
        val limits = properties.provisioning
        val budget = minOf(limits.podsPerRun, limits.maxPreparing - all.count { it.state == ServerState.PREPARING })
        if (budget <= 0) return
        val groupCache = mutableMapOf<GroupId, Group?>()
        all
            .filter { it.state == ServerState.REQUESTED }
            .sortedWith(compareBy({ it.stateSince }, { it.id }))
            .asSequence()
            .mapNotNull { server ->
                groupCache.getOrPut(server.groupId) { groups.find(server.groupId)?.value }.let {
                    server to
                        it
                }
            }.filter { (server, group) -> startable(server, group) }
            .take(budget)
            .forEach { (server, group) -> provision(server, group ?: return@forEach, settings) }
    }

    private fun eulaAccepted(settings: NetworkSettings): Boolean {
        if (settings.eulaAccepted) {
            waitingForEula.set(false)
            return true
        }
        if (!waitingForEula.getAndSet(
                true,
            )
        ) {
            log.warn("Servers wait until a network owner accepts the Minecraft EULA (D-0046)")
        }
        return false
    }

    private fun startable(
        server: Server,
        group: Group?,
    ): Boolean {
        if (group == null) {
            lifecycle.stopNow(server.id, SYSTEM, "group ${server.groupId} no longer exists")
            return false
        }
        // Backing-off and paused groups keep their requests; the deadline sweeper ends ones that wait too long.
        return breaker.status(group.id) == CrashLoopStatus.Healthy
    }

    private fun provision(
        server: Server,
        group: Group,
        settings: NetworkSettings,
    ) {
        try {
            val profile =
                RuntimeProfile.find(server.profileId)
                    ?: throw IllegalArgumentException("Unknown runtime profile ${server.profileId}")
            if (group.type == GroupType.STATIC) pods.ensureVolumeClaim(builder.staticVolumeClaim(group))
            pods.create(builder.pod(server, group, profile, settings.defaultGroupId))
            lifecycle.podCreated(server.id)
        } catch (e: IllegalArgumentException) {
            // A configuration the pod can never be built from: retrying would not help.
            log.error("Cannot build a pod for server {}: {}", server.id, e.message)
            lifecycle.stopNow(server.id, SYSTEM, "invalid configuration: ${e.message}")
        }
    }

    private companion object {
        val SYSTEM = Actor.system("controller")
    }
}
