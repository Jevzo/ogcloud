package io.ogwars.cloud.controller.login

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.node.v1.ResolveLoginResponse
import io.ogwars.cloud.controller.readmodel.ReadModelMessages
import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Decides a login on the controller, so the proxy waits for one answer instead of polling (v1): it records the
 * player, resolves their rank and writes the session. A reconnect replaces the session; the old proxy's late
 * disconnect then no longer matches it (D-0069).
 */
@Component
class LoginResolver(
    private val players: PlayerStore,
    private val permissionGroups: PermissionGroupStore,
    private val network: NetworkSettingsStore,
    private val sessions: SessionStore,
    private val readModels: ReadModelStore,
    private val clock: Clock,
) {
    fun resolve(
        playerUuid: UUID,
        name: String,
        proxyId: UUID,
    ): ResolveLoginResponse {
        val now = clock.instant()
        val settings = network.load().value
        val (player, group) = rank(playerUuid, name, now)
        // With the permission system off, the proxy asks its own permission plugin for the bypass (ProxyRuntime.login).
        if (settings.maintenance && settings.permissionSystemEnabled &&
            !group.grants(PermissionNodes.MAINTENANCE_BYPASS)
        ) {
            return ResolveLoginResponse
                .newBuilder()
                .setAllowed(false)
                .setKickMessage(settings.maintenanceKickMessage)
                .build()
        }
        return ResolveLoginResponse
            .newBuilder()
            .setAllowed(true)
            .setProfile(checkNotNull(open(player, group, proxyId, serverId = null, now, replace = true)))
            .build()
    }

    /**
     * Reopens the session of a player a proxy reports online although none is stored, e.g. after a lost login
     * or a Valkey failover (D-0130). The player is already in, so maintenance does not apply. False when a login
     * created a session meanwhile, which then stays.
     */
    fun restore(
        playerUuid: UUID,
        name: String,
        proxyId: UUID,
        serverId: UUID?,
        connectedAt: Instant,
    ): Boolean {
        val (player, group) = rank(playerUuid, name, clock.instant())
        return open(player, group, proxyId, serverId, connectedAt, replace = false) != null
    }

    /** Records the player and resolves the rank that applies now. */
    private fun rank(
        playerUuid: UUID,
        name: String,
        now: Instant,
    ): Pair<Player, PermissionGroup> {
        val defaultGroup = permissionGroups.findDefault().value
        val seen = players.recordSeen(playerUuid, name, now, PlayerRank(playerUuid, defaultGroup.id, expiresAt = null))
        // Until the expiry loop persists it, an expired rank already counts as the default group.
        val player =
            if (seen.rank.isExpired(
                    now,
                )
            ) {
                seen.copy(rank = PlayerRank(playerUuid, defaultGroup.id, null))
            } else {
                seen
            }
        return player to (permissionGroups.find(player.rank.groupId)?.value ?: defaultGroup)
    }

    /** Writes the session and profile; null when only creating ([replace] false) and a session exists. */
    private fun open(
        player: Player,
        group: PermissionGroup,
        proxyId: UUID,
        serverId: UUID?,
        connectedAt: Instant,
        replace: Boolean,
    ): PlayerProfile? {
        val session =
            PlayerSession(player.uuid, player.name, proxyId, serverId, group.id, player.rankVersion, connectedAt)
        if (replace) {
            sessions.put(session)
        } else if (!sessions.createIfAbsent(session)) {
            return null
        }
        val profile = ReadModelMessages.playerProfile(player.copy(rank = player.rank.copy(groupId = group.id)))
        readModels.put(ReadModelKind.PLAYER_PROFILE, player.uuid.toString(), profile.toByteArray())
        return profile
    }
}
