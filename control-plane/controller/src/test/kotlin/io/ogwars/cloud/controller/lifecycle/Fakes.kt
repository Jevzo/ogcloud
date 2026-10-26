package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.NetworkSettings
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
}

class RecordingServerEvents : ServerEvents {
    val transitions = mutableListOf<Triple<ServerState, ServerState, String>>()
    val transfers = mutableListOf<Pair<UUID, TransferTarget>>()
    val pausedGroups = mutableListOf<GroupId>()

    override fun stateChanged(
        change: ServerChange,
        actor: Actor,
        reason: String,
    ) {
        transitions += Triple(change.before.state, change.after.state, reason)
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
        groups.remove(id)
    }

    override fun restore(id: GroupId) = Unit

    override fun deletedBefore(cutoff: Instant): List<GroupId> = emptyList()

    override fun purge(
        id: GroupId,
        deletedBefore: Instant,
    ) = false
}

class FixedNetworkSettingsStore(
    private val settings: NetworkSettings,
) : NetworkSettingsStore {
    override fun load() = Revisioned(settings, 0)

    override fun save(
        expectedVersion: Long,
        settings: NetworkSettings,
    ) = throw UnsupportedOperationException("Network settings are fixed in this test")
}
