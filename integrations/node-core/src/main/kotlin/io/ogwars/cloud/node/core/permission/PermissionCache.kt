package io.ogwars.cloud.node.core.permission

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.contracts.events.v1.PermissionGroupUpdated
import io.ogwars.cloud.contracts.events.v1.PlayerPermissionsUpdated
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.query.NodeQueries
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** A player's rank as nodes apply it: their profile and their permission group. */
data class Rank(
    val profile: PlayerProfile,
    val group: PermissionGroup,
) {
    /** The exact node, or everything for the wildcard (as the gateway's admin checks). */
    fun grants(node: String): Boolean =
        node in group.permissionsList || PermissionNodes.WILDCARD in group.permissionsList
}

/** What OgCloud says about a permission; [DEFAULT] leaves it to the platform, e.g. with the permission system off. */
enum class Decision { ALLOW, DENY, DEFAULT }

/**
 * Ranks of the players on this node, loaded from the gateway's read models on join and refreshed by broadcasts.
 * Updates older than the held version are ignored.
 */
class PermissionCache(
    private val queries: NodeQueries,
) {
    private val ranks = ConcurrentHashMap<UUID, Rank>()

    // A rank that finishes loading after its player left is dropped instead of kept forever.
    private val present = ConcurrentHashMap.newKeySet<UUID>()

    fun rank(player: UUID): Rank? = ranks[player]

    /**
     * Loads [player]'s rank; a proxy passes the profile its login resolution returned, a backend asks the gateway.
     * Completes with null when the player left meanwhile or has no profile.
     */
    fun load(
        player: UUID,
        profile: PlayerProfile? = null,
    ): CompletableFuture<Rank?> {
        present.add(player)
        val loading = profile?.let { CompletableFuture.completedFuture(it) } ?: queries.profile(player)
        return loading.thenCompose { loaded ->
            if (loaded ==
                null
            ) {
                CompletableFuture.completedFuture(null)
            } else {
                rankOf(loaded).thenApply { store(player, it) }
            }
        }
    }

    /** Loads every held rank again, e.g. after a reconnect missed broadcasts; completes with the players reloaded. */
    fun reloadAll(): CompletableFuture<Set<UUID>> {
        val loads = ranks.keys.map { player -> load(player).thenApply { rank -> player.takeIf { rank != null } } }
        return CompletableFuture
            .allOf(*loads.toTypedArray())
            .thenApply { loads.mapNotNullTo(mutableSetOf()) { it.join() } }
    }

    fun unload(player: UUID) {
        present.remove(player)
        ranks.remove(player)
    }

    fun decide(
        player: UUID,
        node: String,
        permissionSystemEnabled: Boolean,
    ): Decision {
        if (!permissionSystemEnabled) return Decision.DEFAULT
        val rank = ranks[player] ?: return Decision.DENY
        return if (rank.grants(node)) Decision.ALLOW else Decision.DENY
    }

    /** A player's rank changed; completes with the new rank when the player is here and the update is newer. */
    fun refresh(update: PlayerPermissionsUpdated): CompletableFuture<Rank?> {
        val player = UUID.fromString(update.playerUuid)
        val held = ranks[player] ?: return CompletableFuture.completedFuture(null)
        if (update.version < held.profile.version) return CompletableFuture.completedFuture(null)
        return load(player)
    }

    /** A permission group changed or went; completes with the players whose rank it changed. */
    fun refresh(update: PermissionGroupUpdated): CompletableFuture<Set<UUID>> {
        val holders = ranks.filterValues { it.group.permissionGroupId == update.permissionGroupId }.keys
        if (holders.isEmpty() || update.deleted) return CompletableFuture.completedFuture(emptySet())
        return queries.permissionGroup(update.permissionGroupId).thenApply { group ->
            if (group == null) emptySet() else holders.filterTo(mutableSetOf()) { replaceGroup(it, group) }
        }
    }

    /** True when [player] still held the group and [group] is not older than their copy. */
    private fun replaceGroup(
        player: UUID,
        group: PermissionGroup,
    ): Boolean {
        var replaced = false
        ranks.computeIfPresent(player) { _, rank ->
            replaced = rank.group.permissionGroupId == group.permissionGroupId && group.version >= rank.group.version
            if (replaced) rank.copy(group = group) else rank
        }
        return replaced
    }

    private fun rankOf(profile: PlayerProfile): CompletableFuture<Rank?> =
        queries.permissionGroup(profile.permissionGroupId).thenApply { group -> group?.let { Rank(profile, it) } }

    /** Keeps the newer of two concurrently loaded ranks, and nothing for a player who left. */
    private fun store(
        player: UUID,
        rank: Rank?,
    ): Rank? {
        if (rank == null) return null
        return ranks.compute(player) { _, held ->
            when {
                player !in present -> null
                held != null && held.profile.version > rank.profile.version -> held
                else -> rank
            }
        }
    }
}
