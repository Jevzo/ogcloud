package io.ogwars.cloud.controller.login

import io.ogwars.cloud.contracts.node.v1.ResolveLoginResponse
import io.ogwars.cloud.controller.readmodel.ReadModelMessages
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Component
import java.time.Clock
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
        val group = permissionGroups.find(player.rank.groupId)?.value ?: defaultGroup
        // With the permission system off, the proxy checks bypass with its own permission plugin (T-0504).
        if (settings.maintenance && settings.permissionSystemEnabled && !group.grants(MAINTENANCE_BYPASS)) {
            return ResolveLoginResponse
                .newBuilder()
                .setAllowed(
                    false,
                ).setKickMessage(settings.maintenanceKickMessage)
                .build()
        }
        sessions.put(
            PlayerSession(playerUuid, player.name, proxyId, serverId = null, group.id, player.rankVersion, now),
        )
        val profile = ReadModelMessages.playerProfile(player.copy(rank = player.rank.copy(groupId = group.id)))
        readModels.put(ReadModelKind.PLAYER_PROFILE, playerUuid.toString(), profile.toByteArray())
        return ResolveLoginResponse
            .newBuilder()
            .setAllowed(true)
            .setProfile(profile)
            .build()
    }

    companion object {
        const val MAINTENANCE_BYPASS = "ogcloud.maintenance.bypass"
    }
}
