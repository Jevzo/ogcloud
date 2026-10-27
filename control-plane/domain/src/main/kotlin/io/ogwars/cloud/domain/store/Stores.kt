package io.ogwars.cloud.domain.store

import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.retention.RetentionKind
import io.ogwars.cloud.domain.runtime.RuntimeArtifact
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.template.TemplateVersion
import java.time.Instant
import java.util.UUID

// Ports for persistent state (D-0009); the persistence module implements them.

interface GroupStore {
    /** Groups that are not soft-deleted, with the version to present when updating them. */
    fun findAll(): List<Revisioned<Group>>

    fun find(id: GroupId): Revisioned<Group>?

    fun create(group: Group)

    /** Replaces the group if it still has [expectedVersion]; otherwise throws [StaleUpdateException]. */
    fun update(
        expectedVersion: Long,
        group: Group,
    )

    fun softDelete(
        id: GroupId,
        at: Instant,
    )

    fun restore(id: GroupId)

    /** Every soft-deleted group, whatever its deletion time. */
    fun deleted(): List<GroupId>

    fun deletedBefore(cutoff: Instant): List<GroupId>

    /**
     * Removes a group soft-deleted before [deletedBefore], with its template versions and NPCs.
     * Returns false if it was restored meanwhile.
     */
    fun purge(
        id: GroupId,
        deletedBefore: Instant,
    ): Boolean
}

interface NetworkSettingsStore {
    fun load(): Revisioned<NetworkSettings>

    /** Saves if the settings still have [expectedVersion]; otherwise throws [StaleUpdateException]. */
    fun save(
        expectedVersion: Long,
        settings: NetworkSettings,
    )
}

/** Reads carry the row version, which read models and broadcasts use so nodes can drop stale updates. */
interface PermissionGroupStore {
    fun findAll(): List<Revisioned<PermissionGroup>>

    fun find(id: String): Revisioned<PermissionGroup>?

    fun findDefault(): Revisioned<PermissionGroup>

    fun save(group: PermissionGroup)

    fun delete(id: String)
}

interface PlayerStore {
    fun find(uuid: UUID): Player?

    fun findByName(name: String): Player?

    /** Creates the player on first sight with [defaultRank], otherwise refreshes name and last-seen. */
    fun recordSeen(
        uuid: UUID,
        name: String,
        at: Instant,
        defaultRank: PlayerRank,
    ): Player

    /** Stores the rank and returns the new rank version. */
    fun updateRank(rank: PlayerRank): Long

    fun withExpiredRanks(
        now: Instant,
        limit: Int,
    ): List<Player>

    /**
     * Moves the player to [defaultGroupId] only if their rank is still expired at [now]; returns the new rank version,
     * or null when an admin changed the rank meanwhile.
     */
    fun resetExpiredRank(
        playerUuid: UUID,
        defaultGroupId: String,
        now: Instant,
    ): Long?
}

interface TemplateVersionStore {
    fun add(version: TemplateVersion)

    fun findByGroup(groupId: GroupId): List<TemplateVersion>

    fun find(id: UUID): TemplateVersion?

    fun delete(id: UUID)
}

interface NpcStore {
    fun findByGroup(groupId: GroupId): List<Npc>

    fun find(id: UUID): Npc?

    fun save(npc: Npc)

    fun delete(id: UUID)
}

interface JobStore {
    fun create(job: Job)

    fun find(id: UUID): Job?

    /** Saves if the stored job still has [Job.version]; returns it with the new version or throws [StaleUpdateException]. */
    fun update(job: Job): Job

    /** Queued and running jobs, oldest first, for a new leader to resume. */
    fun findActive(): List<Job>
}

interface AuditLogStore {
    fun append(entry: AuditEntry)
}

interface ScalingLogStore {
    fun append(entry: ScalingLogEntry)
}

interface RuntimeArtifactStore {
    fun findByProfile(profileId: String): List<RuntimeArtifact>

    fun save(artifact: RuntimeArtifact)

    fun delete(
        profileId: String,
        path: String,
    )
}

/** Batched deletes for the cleanup loop (D-0043). */
interface RetentionStore {
    /** Kinds that have a table yet; credentials join with the API's tables (T-0302). */
    val kinds: Set<RetentionKind>

    /** For dry runs: how many records of [kind] are older than [cutoff]. */
    fun countOlderThan(
        kind: RetentionKind,
        cutoff: Instant,
    ): Long

    /** Deletes up to [limit] records of [kind] older than [cutoff]; returns how many. */
    fun deleteOlderThan(
        kind: RetentionKind,
        cutoff: Instant,
        limit: Int,
    ): Int
}

/** The record changed since it was read; reload and decide again. */
class StaleUpdateException(
    message: String,
) : RuntimeException(message)
