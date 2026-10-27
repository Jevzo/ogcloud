package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.TransferTarget
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.Revisioned
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class RecordingPodGateway : PodGateway {
    val created = mutableListOf<Pod>()
    val deleted = mutableListOf<Pair<String, Duration?>>()
    val safeToEvict = mutableMapOf<String, Boolean>()
    val claims = mutableListOf<PersistentVolumeClaim>()

    override fun create(pod: Pod) {
        created += pod
    }

    override fun delete(
        podName: String,
        grace: Duration?,
    ) {
        deleted += podName to grace
    }

    override fun setSafeToEvict(
        podName: String,
        safe: Boolean,
    ) {
        safeToEvict[podName] = safe
    }

    override fun ensureVolumeClaim(claim: PersistentVolumeClaim) {
        claims += claim
    }

    override fun list(): List<Pod> = created.filter { pod -> deleted.none { it.first == pod.metadata.name } }

    override fun listVolumeClaims(): List<PersistentVolumeClaim> = claims.toList()

    override fun deleteVolumeClaim(name: String) {
        claims.removeIf { it.metadata.name == name }
    }
}

class RecordingServerEvents : ServerEvents {
    val transitions = mutableListOf<Triple<ServerState, ServerState, String>>()

    /** Groups of newly requested servers, in request order. */
    val requestedGroups = mutableListOf<GroupId>()
    val transfers = mutableListOf<Pair<UUID, TransferTarget>>()
    val pausedGroups = mutableListOf<GroupId>()
    val scalings = mutableListOf<ScalingLogEntry>()

    override fun stateChanged(
        change: ServerChange,
        actor: Actor,
        reason: String,
    ) {
        transitions += Triple(change.before.state, change.after.state, reason)
        if (change.before == change.after &&
            change.after.state == ServerState.REQUESTED
        ) {
            requestedGroups += change.after.groupId
        }
    }

    override fun transferPlayers(
        fromServer: UUID,
        target: TransferTarget,
        reason: String,
    ) {
        transfers += fromServer to target
    }

    override fun crashLoopPaused(
        groupId: GroupId,
        failures: Int,
    ) {
        pausedGroups += groupId
    }

    override fun scalingDecided(entry: ScalingLogEntry) {
        scalings += entry
    }
}

/** A clock the test moves forward explicitly. */
class MutableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advance(by: Duration) {
        now = now.plus(by)
    }
}

class InMemoryGroupStore(
    groups: List<Group>,
) : GroupStore {
    private val groups = groups.associateBy { it.id }.toMutableMap()
    private val deleted = mutableMapOf<GroupId, Pair<Group, Instant>>()

    override fun findAll() = groups.values.map { Revisioned(it, 0) }

    override fun find(id: GroupId) = groups[id]?.let { Revisioned(it, 0) }

    override fun create(group: Group) {
        groups[group.id] = group
    }

    override fun update(
        expectedVersion: Long,
        group: Group,
    ) {
        groups[group.id] = group
    }

    override fun softDelete(
        id: GroupId,
        at: Instant,
    ) {
        groups.remove(id)?.let { deleted[id] = it to at }
    }

    override fun restore(id: GroupId) {
        deleted.remove(id)?.let { groups[id] = it.first }
    }

    override fun deleted(): List<GroupId> = deleted.keys.toList()

    override fun deletedBefore(cutoff: Instant): List<GroupId> =
        deleted
            .filterValues {
                it.second.isBefore(cutoff)
            }.keys
            .toList()

    override fun purge(
        id: GroupId,
        deletedBefore: Instant,
    ): Boolean = deleted[id]?.takeIf { it.second.isBefore(deletedBefore) }?.let { deleted.remove(id) } != null
}

class InMemoryNetworkSettingsStore(
    settings: NetworkSettings,
) : NetworkSettingsStore {
    private var current = Revisioned(settings, 0)

    override fun load() = current

    override fun save(
        expectedVersion: Long,
        settings: NetworkSettings,
    ) {
        check(expectedVersion == current.revision) { "stale settings" }
        current = Revisioned(settings, current.revision + 1)
    }
}
