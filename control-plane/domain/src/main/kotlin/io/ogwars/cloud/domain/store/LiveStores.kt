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

    /** As [create], atomically refused while any other server of the group exists (STATIC groups share one volume). */
    fun createAlone(server: Server): Boolean

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

    /** Online players ordered by uuid, starting after [after]; sessions that expired meanwhile are skipped. */
    fun listOnline(
        after: UUID?,
        limit: Int,
    ): List<PlayerSession>
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

    /** A group's active template for the gateway's GetPodAssets (D-0081). */
    ACTIVE_TEMPLATE("template"),
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

    /** Every stored id of [kind], so a resync can drop read models whose entity is gone (D-0100). */
    fun ids(kind: ReadModelKind): Set<String>
}

/** Refresh tokens rotated away, so presenting one again is detected as reuse (D-0090). */
interface RotatedTokenStore {
    fun remember(
        tokenHash: String,
        authorizationId: String,
        ttl: Duration,
    )

    fun authorizationOf(tokenHash: String): String?
}

/** A token bucket: [capacity] requests at once, refilled at [perMinute] (D-0086). */
data class RateLimit(
    val capacity: Int,
    val perMinute: Int,
) {
    init {
        require(capacity > 0 && perMinute > 0) { "Rate limits must be positive" }
    }
}

/** Shared by every API replica; [retryAfter] is zero when the request may go ahead. */
data class RateDecision(
    val allowed: Boolean,
    val retryAfter: Duration,
)

fun interface RateLimiter {
    /** Takes a token from [client]'s bucket for one class of requests, e.g. sign-ins. */
    fun tryAcquire(
        bucket: String,
        client: String,
        limit: RateLimit,
        at: Instant,
    ): RateDecision
}

/** What a one-time value is for; it namespaces the keys. */
enum class OneTimeKind(
    val key: String,
) {
    /** Lets an EventSource, which cannot send headers, open the live-update stream (D-0105). */
    SSE_TICKET("sse-ticket"),
}

/** Values that can be redeemed once before they expire; ids are hashes, never the secrets themselves. */
interface OneTimeStore {
    fun put(
        kind: OneTimeKind,
        id: String,
        value: String,
        ttl: Duration,
    )

    /** Returns and deletes the value, atomically; null if it was taken or expired. */
    fun take(
        kind: OneTimeKind,
        id: String,
    ): String?
}
