package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Component
import java.util.UUID

/** One path for every rank change (admin change, expiry, re-enable sync): read model, session, then broadcast. */
@Component
class PlayerPermissions(
    private val players: PlayerStore,
    private val permissionGroups: PermissionGroupStore,
    private val sessions: SessionStore,
    private val readModels: ReadModelStore,
    private val broadcasts: NodeBroadcasts,
) {
    fun publish(playerUuid: UUID) {
        players.find(playerUuid)?.let(::publish)
    }

    fun publish(player: Player) {
        readModels.put(
            ReadModelKind.PLAYER_PROFILE,
            player.uuid.toString(),
            ReadModelMessages.playerProfile(player).toByteArray(),
        )
        if (updateSession(
                player,
            )
        ) {
            broadcasts.playerPermissions(player.uuid.toString(), player.rank.groupId, player.rankVersion)
        }
    }

    /** Resets an expired rank to the default group. */
    fun expire(player: Player) {
        val defaultGroup = permissionGroups.findDefault().value.id
        val version = players.updateRank(PlayerRank(player.uuid, defaultGroup, expiresAt = null))
        publish(player.copy(rank = PlayerRank(player.uuid, defaultGroup, null), rankVersion = version))
    }

    /** Online players only; true when the session now carries this rank, so their proxy must hear about it. */
    private fun updateSession(player: Player): Boolean {
        repeat(MAX_ATTEMPTS) {
            val session = sessions.get(player.uuid) ?: return false
            if (session.value.rankVersion >= player.rankVersion) return session.value.rankVersion == player.rankVersion
            val updated = session.value.copy(permissionGroupId = player.rank.groupId, rankVersion = player.rankVersion)
            if (sessions.compareAndSet(session.revision, updated)) return true
        }
        throw IllegalStateException("Session of ${player.uuid} kept changing")
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
