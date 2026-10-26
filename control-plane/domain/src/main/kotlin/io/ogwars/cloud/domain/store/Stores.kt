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
    /** Groups that are not soft-deleted. */
    fun findAll(): List<Group>

    fun find(id: GroupId): Group?

    fun create(group: Group)

    /** Overwrites the stored group and bumps its version. */
    fun update(group: Group)

    fun softDelete(
        id: GroupId,
        at: Instant,
    )

    fun restore(id: GroupId)

    fun deletedBefore(cutoff: Instant): List<GroupId>

    /** Removes the group with its template versions and NPCs. */
    fun purge(id: GroupId)
}

interface NetworkSettingsStore {
    fun load(): NetworkSettings

    fun save(settings: NetworkSettings)
}

interface PermissionGroupStore {
    fun findAll(): List<PermissionGroup>

    fun find(id: String): PermissionGroup?

    fun findDefault(): PermissionGroup

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
}

interface TemplateVersionStore {
    fun add(version: TemplateVersion)

    fun findByGroup(groupId: GroupId): List<TemplateVersion>

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

    fun update(job: Job)

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
    /** Deletes up to [limit] records of [kind] older than [cutoff]; returns how many. */
    fun deleteOlderThan(
        kind: RetentionKind,
        cutoff: Instant,
        limit: Int,
    ): Int
}
