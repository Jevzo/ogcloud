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
import io.ogwars.cloud.domain.user.Role
import io.ogwars.cloud.domain.user.WebUser
import java.time.Instant
import java.util.UUID

// Ports for persistent state (D-0009); the persistence module implements them.

/** A soft-deleted group, restorable until its purge (D-0043). */
data class DeletedGroup(
    val group: Group,
    val deletedAt: Instant,
)

interface GroupStore {
    /** Groups that are not soft-deleted, with the version to present when updating them. */
    fun findAll(): List<Revisioned<Group>>

    fun find(id: GroupId): Revisioned<Group>?

    /** Throws [ConflictException] if the id is taken, also by a deleted group awaiting its purge. */
    fun create(group: Group)

    /** Replaces the group if it still has [expectedVersion]; otherwise throws [StaleUpdateException]. */
    fun update(
        expectedVersion: Long,
        group: Group,
    )

    /** False if no active group has [id]. */
    fun softDelete(
        id: GroupId,
        at: Instant,
    ): Boolean

    /** False if no deleted group has [id]. */
    fun restore(id: GroupId): Boolean

    /** Every soft-deleted group, whatever its deletion time, oldest deletion first. */
    fun findDeleted(): List<DeletedGroup>

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

    /** A new default group takes the flag from the old one. Throws [ConflictException] if the id is taken. */
    fun create(group: PermissionGroup)

    /**
     * As [GroupStore.update]. A group becomes the default by setting [PermissionGroup.isDefault]; the default
     * cannot drop the flag itself ([ConflictException]), another group takes it over.
     */
    fun update(
        expectedVersion: Long,
        group: PermissionGroup,
    )

    /**
     * Moves the group's holders to the default group, permanently, and deletes the group in one transaction
     * (D-0102). Returns the moved players; throws [ConflictException] for the default group.
     */
    fun delete(id: String): List<UUID>
}

interface PlayerStore {
    fun find(uuid: UUID): Player?

    fun findByName(name: String): Player?

    /** The current names of those of [uuids] that were seen. */
    fun names(uuids: Collection<UUID>): Map<UUID, String>

    /** Forgets the player (D-0049); a linked web user keeps the account but loses the link. */
    fun delete(uuid: UUID): Boolean

    /** Every player seen, ordered by uuid after [after]; [namePrefix] narrows them, ignoring case. */
    fun list(
        namePrefix: String?,
        after: UUID?,
        limit: Int,
    ): List<Player>

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
    fun findAll(): List<Npc>

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

    /** Newest first, starting below [before]; ids are UUIDv7, so this is creation order (D-0064). */
    fun list(
        before: UUID?,
        limit: Int,
    ): List<Job>
}

interface AuditLogStore {
    fun append(entry: AuditEntry)

    /** Newest first, below [before]; ids are UUIDv7 (D-0064). */
    fun list(
        before: UUID?,
        limit: Int,
    ): List<AuditEntry>

    /** Every entry a user or player [identifier] caused or was the target of, newest first (D-0049). */
    fun about(identifier: String): List<AuditEntry>
}

/** Records that outlive an erasure: audit entries, job and template authors (D-0049). */
interface PseudonymStore {
    /** Replaces the user or player [identifier] with [pseudonym]; returns how many records changed. */
    fun pseudonymise(
        identifier: String,
        pseudonym: String,
    ): Int
}

interface ScalingLogStore {
    fun append(entry: ScalingLogEntry)

    /** Newest first, below [before], optionally for one group. */
    fun list(
        groupId: GroupId?,
        before: UUID?,
        limit: Int,
    ): List<ScalingLogEntry>
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

/** The change conflicts with current state, e.g. it would leave the network without an owner. */
class ConflictException(
    message: String,
) : RuntimeException(message)

interface WebUserStore {
    fun find(id: UUID): Revisioned<WebUser>?

    /** By username or email, ignoring case. */
    fun findByLogin(login: String): WebUser?

    /** The user linked to the Minecraft account [playerUuid], if any (D-0106). */
    fun findByLinkedPlayer(playerUuid: UUID): Revisioned<WebUser>?

    /** Ordered by id, i.e. by creation (D-0064), starting after [after]. */
    fun list(
        after: UUID?,
        limit: Int,
    ): List<Revisioned<WebUser>>

    /** Throws [ConflictException] if the username or email is taken. */
    fun create(user: WebUser)

    /** Creates [owner] unless an owner exists, atomically across API replicas; false if one does (D-0094). */
    fun createFirstOwner(owner: WebUser): Boolean

    fun ownerExists(): Boolean

    /**
     * Replaces the user if it still has [expectedVersion], else throws [StaleUpdateException]. Throws
     * [ConflictException] if the change would leave no enabled owner or reuse a taken username or email.
     */
    fun update(
        expectedVersion: Long,
        user: WebUser,
    )

    fun setPasswordHash(
        id: UUID,
        passwordHash: String,
    )

    /** Throws [ConflictException] for the last enabled owner. */
    fun delete(id: UUID)
}

interface RoleStore {
    /** Built-in roles first, with the permissions defined in code (D-0089). */
    fun findAll(): List<Revisioned<Role>>

    fun find(id: String): Revisioned<Role>?

    /** Throws [ConflictException] if the id is taken. */
    fun create(role: Role)

    /** As [GroupStore.update]; built-in roles cannot change ([ConflictException]). */
    fun update(
        expectedVersion: Long,
        role: Role,
    )

    /** Throws [ConflictException] for built-in roles and roles still assigned to users. */
    fun delete(id: String)
}

/** A user's TOTP secret, encrypted by the API under [keyId] (D-0097). */
data class StoredTotp(
    val userId: UUID,
    val keyId: String,
    val encryptedSecret: ByteArray,
    val confirmedAt: Instant? = null,
    val lastUsedStep: Long? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is StoredTotp &&
            userId == other.userId &&
            keyId == other.keyId &&
            encryptedSecret.contentEquals(other.encryptedSecret) &&
            confirmedAt == other.confirmedAt &&
            lastUsedStep == other.lastUsedStep

    override fun hashCode(): Int = userId.hashCode()
}

interface TotpStore {
    fun find(userId: UUID): StoredTotp?

    /** Starts an enrollment, replacing an unconfirmed one; false while a confirmed one exists. */
    fun startEnrollment(totp: StoredTotp): Boolean

    /** Confirms the enrollment with the step its first code used; false if there is none. */
    fun confirm(
        userId: UUID,
        at: Instant,
        step: Long,
    ): Boolean

    /** Records [step] as used if it is newer than the last one; false for a replayed code. */
    fun markUsed(
        userId: UUID,
        step: Long,
    ): Boolean

    fun delete(userId: UUID)
}
