package io.ogwars.cloud.domain.store

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.session.PlayerSession
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Ports for live state in Valkey (D-0062); the livestate module implements them.

/** A value with the revision a compare-and-set must present to replace it. */
data class Revisioned<T>(
    val value: T,
    val revision: Long,
)

interface ServerStateStore {
    /** Stores a new server; false if one with this id already exists. */
    fun create(server: Server): Boolean

    fun get(id: UUID): Revisioned<Server>?

    fun listAll(): List<Revisioned<Server>>

    fun listByGroup(groupId: GroupId): List<Revisioned<Server>>

    /** Replaces the server only if it still has [expectedRevision]. */
    fun compareAndSet(
        expectedRevision: Long,
        updated: Server,
    ): Boolean

    fun delete(server: Server)
}

interface SessionStore {
    /** Creates or replaces the session at login. */
    fun put(session: PlayerSession)

    fun get(playerUuid: UUID): Revisioned<PlayerSession>?

    fun findByName(name: String): Revisioned<PlayerSession>?

    fun compareAndSet(
        expectedRevision: Long,
        updated: PlayerSession,
    ): Boolean

    /** Removes the session only if it is still the one read; a reconnect in between keeps the new one. */
    fun remove(session: Revisioned<PlayerSession>): Boolean

    fun onlinePlayers(): Set<UUID>
}

/** Mutual exclusion across replicas, e.g. for restarts or the permission re-enable sync. */
interface LockManager {
    /** Returns the lock, or null when someone else holds it. */
    fun tryAcquire(
        name: String,
        lease: Duration,
    ): Lock?
}

interface Lock : AutoCloseable {
    val name: String

    /** Extends the lease; false if the lock was lost meanwhile. */
    fun extend(lease: Duration): Boolean

    /** Releases the lock if this holder still owns it. */
    override fun close()
}

interface ScalingCooldownStore {
    fun lastScaledAt(groupId: GroupId): Instant?

    fun record(
        groupId: GroupId,
        at: Instant,
        cooldown: Duration,
    )
}

interface CrashFailureStore {
    /** Records [serverId]'s failure and forgets failures older than [window]. */
    fun record(
        groupId: GroupId,
        serverId: UUID,
        at: Instant,
        window: Duration,
    )

    fun failures(groupId: GroupId): List<Instant>

    /** Latches the pause; it outlives the failure window. */
    fun pause(groupId: GroupId)

    fun isPaused(groupId: GroupId): Boolean

    /** Called when an admin resumes a paused group: clears the latch and the failures. */
    fun clear(groupId: GroupId)
}

enum class ReadModelKind(
    val key: String,
) {
    NETWORK_SETTINGS("network-settings"),
    GROUP("group"),
    PERMISSION_GROUP("permission-group"),
    PLAYER_PROFILE("player-profile"),
    NPCS_BY_GROUP("npcs-by-group"),

    /** A profile's bundle manifest for the gateway's GetPodAssets (D-0080). */
    RUNTIME_BUNDLE("runtime-bundle"),
}

/** Encoded read models the gateway and load balancer serve from (D-0007); the controller writes them. */
interface ReadModelStore {
    fun put(
        kind: ReadModelKind,
        id: String,
        encoded: ByteArray,
    )

    fun get(
        kind: ReadModelKind,
        id: String,
    ): ByteArray?

    fun delete(
        kind: ReadModelKind,
        id: String,
    )
}
