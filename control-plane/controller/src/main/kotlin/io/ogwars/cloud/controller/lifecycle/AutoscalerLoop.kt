package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.scaling.Autoscaler
import io.ogwars.cloud.domain.scaling.GroupSnapshot
import io.ogwars.cloud.domain.scaling.ScalingAction
import io.ogwars.cloud.domain.scaling.ScalingDecision
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.ScalingCooldownStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Applies the domain [Autoscaler] to every group. The cooldown lives in Valkey, so a new leader keeps it. Drain
 * timeouts are the deadline sweeper's job, so they still run while maintenance pauses scaling.
 */
@Component
class AutoscalerLoop(
    private val servers: ServerStateStore,
    private val groups: GroupStore,
    private val network: NetworkSettingsStore,
    private val cooldowns: ScalingCooldownStore,
    private val failures: CrashFailureStore,
    private val autoscaler: Autoscaler,
    private val lifecycle: LifecycleService,
    private val scalingLog: ScalingLogStore,
    private val events: ServerEvents,
    private val ids: IdGenerator,
    private val properties: LifecycleProperties,
    private val clock: Clock,
    private val meters: MeterRegistry,
) : LeaderLoop {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name = "autoscaler"
    override val interval: Duration get() = properties.scaleInterval

    override fun runOnce() {
        val maintenance = network.load().value.maintenance
        val byGroup = servers.listAll().map { it.value }.groupBy { it.groupId }
        val now = clock.instant()
        groups.findAll().map { it.value }.forEach { group ->
            // One group's failure must not stop scaling for the others.
            try {
                scale(group, byGroup[group.id].orEmpty(), maintenance, now)
            } catch (e: Exception) {
                log.error("Scaling group {} failed", group.id, e)
                Counter
                    .builder(FAILURES_METRIC)
                    .tag("group", group.id.value)
                    .register(meters)
                    .increment()
            }
        }
    }

    private fun scale(
        group: Group,
        members: List<Server>,
        maintenance: Boolean,
        now: Instant,
    ) {
        val snapshot =
            GroupSnapshot(group, members, cooldowns.lastScaledAt(group.id), maintenance, failures.isPaused(group.id))
        val decisions = autoscaler.decide(snapshot, now)
        if (decisions.isEmpty()) return
        decisions.forEach { apply(group, it, now) }
        cooldowns.record(group.id, now, group.scaling.cooldown)
    }

    private fun apply(
        group: Group,
        decision: ScalingDecision,
        now: Instant,
    ) {
        val reason = "autoscaler: ${decision.reason.name.lowercase()}"
        val (action, serverId) =
            when (decision) {
                is ScalingDecision.Start -> {
                    // A STATIC group whose server another caller just requested needs nothing more.
                    ScalingAction.START to (start(group) ?: return)
                }

                is ScalingDecision.Drain -> {
                    lifecycle.drain(decision.serverId, ACTOR, reason)
                    ScalingAction.DRAIN to decision.serverId
                }

                is ScalingDecision.Undrain -> {
                    lifecycle.undrain(decision.serverId, ACTOR, reason)
                    ScalingAction.UNDRAIN to decision.serverId
                }
            }
        val entry = ScalingLogEntry(ids.next(), now, group.id, action, serverId, decision.reason)
        scalingLog.append(entry)
        events.scalingDecided(entry)
        Counter
            .builder(DECISIONS_METRIC)
            .tag("group", group.id.value)
            .tag("action", action.name.lowercase())
            .register(meters)
            .increment()
    }

    private fun start(group: Group): UUID? =
        lifecycle.request(ids.next(), group.id, group.type, group.profileId, ACTOR)?.id

    private companion object {
        val ACTOR = Actor.system("autoscaler")
        const val DECISIONS_METRIC = "ogcloud.controller.scaling.decisions"
        const val FAILURES_METRIC = "ogcloud.controller.scaling.failures"
    }
}
