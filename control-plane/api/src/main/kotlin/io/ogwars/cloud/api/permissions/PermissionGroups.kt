package io.ogwars.cloud.api.permissions

import io.ogwars.cloud.api.events.Announcements
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.permission.RankDuration
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/** The built-in permission system: groups (ranks) and who holds them (D-0102). */
@Service
class PermissionGroups(
    private val groups: PermissionGroupStore,
    private val players: PlayerStore,
    private val sessions: SessionStore,
    private val announcements: Announcements,
    private val clock: Clock,
) {
    fun list(): List<Revisioned<PermissionGroup>> = groups.findAll()

    fun get(id: String): Revisioned<PermissionGroup> =
        groups.find(id) ?: throw NoSuchElementException("Permission group $id does not exist")

    fun create(
        actor: Actor,
        group: PermissionGroup,
    ): Revisioned<PermissionGroup> {
        val previousDefault = groups.findDefault().value.id
        groups.create(group)
        announce(actor, group, previousDefault)
        return get(group.id)
    }

    fun update(
        actor: Actor,
        expectedVersion: Long,
        group: PermissionGroup,
    ): Revisioned<PermissionGroup> {
        val previousDefault = groups.findDefault().value.id
        groups.update(expectedVersion, group)
        announce(actor, group, previousDefault)
        return get(group.id)
    }

    /** Holders fall back to the default group; those online learn it at once. */
    fun delete(
        actor: Actor,
        id: String,
    ) {
        val moved = groups.delete(id)
        announcements.permissionGroup(actor, id, deleted = true)
        val fallback = groups.findDefault().value.id
        moved
            .filter {
                sessions.get(
                    it,
                ) != null
            }.forEach { announcements.playerRank(actor, PlayerRank(it, fallback, null)) }
    }

    fun setRank(
        actor: Actor,
        playerUuid: UUID,
        groupId: String,
        duration: String,
    ): PlayerRank {
        get(groupId)
        players.find(playerUuid) ?: throw NoSuchElementException("Player $playerUuid was never seen")
        val rank = PlayerRank.assign(playerUuid, groupId, RankDuration.parse(duration), clock.instant())
        players.updateRank(rank)
        announcements.playerRank(actor, rank)
        return rank
    }

    private fun announce(
        actor: Actor,
        group: PermissionGroup,
        previousDefault: String,
    ) {
        announcements.permissionGroup(actor, group.id)
        if (group.isDefault && previousDefault != group.id) {
            // The old default lost its flag; both read models change.
            announcements.permissionGroup(actor, previousDefault)
            announcements.defaultPermissionGroup(actor, group.id)
        }
    }
}
