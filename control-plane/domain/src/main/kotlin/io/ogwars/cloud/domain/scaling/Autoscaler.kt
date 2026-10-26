package io.ogwars.cloud.domain.scaling

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.GameState
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class ScalingReason {
    MIN_ONLINE,
    LOAD,
    SCALE_DOWN,
    STATIC,
}

sealed interface ScalingDecision {
    val reason: ScalingReason

    data class Start(
        override val reason: ScalingReason,
    ) : ScalingDecision

    /** The lifecycle policy decides whether a drain becomes an immediate stop. */
    data class Drain(
        val serverId: UUID,
        override val reason: ScalingReason,
    ) : ScalingDecision

    data class Undrain(
        val serverId: UUID,
        override val reason: ScalingReason,
    ) : ScalingDecision
}

/** Everything the autoscaler needs about one group at one moment. */
data class GroupSnapshot(
    val group: Group,
    val servers: List<Server>,
    /** When the last non-empty decision was applied; drives the cooldown. */
    val lastScaledAt: Instant?,
    val networkMaintenance: Boolean,
    /** Set by the crash-loop breaker; a paused group gets no new servers. */
    val paused: Boolean,
)

/** Pure scaling rules: a snapshot in, decisions out. The controller applies them and records the time. */
class Autoscaler {
    fun decide(
        snapshot: GroupSnapshot,
        now: Instant,
    ): List<ScalingDecision> {
        val group = snapshot.group
        if (snapshot.networkMaintenance || group.maintenance || snapshot.paused) return emptyList()
        return when (group.type) {
            GroupType.STATIC -> decideStatic(group, snapshot.servers)
            GroupType.PROXY -> if (onCooldown(snapshot, now)) emptyList() else decideProxies(group, snapshot.servers)
            GroupType.DYNAMIC -> if (onCooldown(snapshot, now)) emptyList() else decideDynamic(group, snapshot.servers)
        }
    }

    private fun onCooldown(
        snapshot: GroupSnapshot,
        now: Instant,
    ): Boolean {
        val last = snapshot.lastScaledAt ?: return false
        return Duration.between(last, now) < snapshot.group.scaling.cooldown
    }

    private fun decideStatic(
        group: Group,
        servers: List<Server>,
    ): List<ScalingDecision> {
        val wanted = group.scaling.minOnline >= 1
        val active = servers.any { it.state != ServerState.STOPPED }
        return if (wanted && !active) listOf(ScalingDecision.Start(ScalingReason.STATIC)) else emptyList()
    }

    private fun decideProxies(
        group: Group,
        servers: List<Server>,
    ): List<ScalingDecision> {
        val scaling = group.scaling
        val running = servers.filter { it.state == ServerState.RUNNING }
        val draining = servers.filter { it.state == ServerState.DRAINING }
        val pending = servers.count { it.state.isPending }
        val totalActive = running.size + pending + draining.size

        val available = running.size + pending
        if (available < scaling.minOnline) {
            // Bringing a draining proxy back is cheaper than starting a new one.
            val deficit = scaling.minOnline - available
            val undrained = draining.take(deficit).map { ScalingDecision.Undrain(it.id, ScalingReason.MIN_ONLINE) }
            val remaining = deficit - undrained.size
            val started =
                if (remaining > 0 && totalActive + remaining <= scaling.maxInstances) {
                    List(remaining) { ScalingDecision.Start(ScalingReason.MIN_ONLINE) }
                } else {
                    emptyList()
                }
            return undrained + started
        }

        val ratio = loadRatio(running, group) ?: return emptyList()
        if (ratio > scaling.scaleUpThreshold) {
            val candidate = draining.firstOrNull()
            return when {
                candidate != null -> listOf(ScalingDecision.Undrain(candidate.id, ScalingReason.LOAD))
                totalActive < scaling.maxInstances -> listOf(ScalingDecision.Start(ScalingReason.LOAD))
                else -> emptyList()
            }
        }
        if (ratio < scaling.scaleDownThreshold && running.size > scaling.minOnline) {
            val quietest = running.minBy { it.players }
            return listOf(ScalingDecision.Drain(quietest.id, ScalingReason.SCALE_DOWN))
        }
        return emptyList()
    }

    private fun decideDynamic(
        group: Group,
        servers: List<Server>,
    ): List<ScalingDecision> {
        val scaling = group.scaling
        // Only lobbies count as capacity, so servers in a running game are never drained.
        val lobbies = servers.filter { it.state == ServerState.RUNNING && it.gameState == GameState.LOBBY }
        val pending = servers.count { it.state.isPending }
        val totalActive = servers.count { it.state != ServerState.STOPPED }

        val available = lobbies.size + pending
        if (available < scaling.minOnline && totalActive < scaling.maxInstances) {
            val toStart = minOf(scaling.minOnline - available, scaling.maxInstances - totalActive)
            return List(toStart) { ScalingDecision.Start(ScalingReason.MIN_ONLINE) }
        }

        val ratio = loadRatio(lobbies, group) ?: return emptyList()
        if (ratio > scaling.scaleUpThreshold) {
            return if (totalActive <
                scaling.maxInstances
            ) {
                listOf(ScalingDecision.Start(ScalingReason.LOAD))
            } else {
                emptyList()
            }
        }
        if (ratio < scaling.scaleDownThreshold) {
            val surplus = lobbies.size - scaling.minOnline
            // The youngest empty lobbies go first.
            return lobbies
                .filter { it.players == 0 }
                .sortedByDescending { it.startedAt }
                .take(maxOf(surplus, 0))
                .map { ScalingDecision.Drain(it.id, ScalingReason.SCALE_DOWN) }
        }
        return emptyList()
    }

    /** Players per available slot, or null when there is no running capacity to measure. */
    private fun loadRatio(
        servers: List<Server>,
        group: Group,
    ): Double? {
        if (servers.isEmpty()) return null
        val capacity = servers.size * group.scaling.playersPerServer
        return servers.sumOf { it.players }.toDouble() / capacity
    }
}
