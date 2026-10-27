package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import org.springframework.stereotype.Component
import java.time.Instant
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

    /** A resync: rewrites a profile that differs and tells the proxy about a rank the session lacks (D-0100). */
    fun resync(playerUuid: UUID) {
        val write = ReadModelWrite.of(readModels, ReadModelKind.PLAYER_PROFILE, playerUuid.toString(), resync = true)
        val player = players.find(playerUuid) ?: return
        readModels.write(
            ReadModelKind.PLAYER_PROFILE,
            playerUuid.toString(),
            ReadModelMessages.playerProfile(player).toByteArray(),
            write,
        )
        val session = sessions.get(playerUuid)?.value ?: return
        if (session.rankVersion < player.rankVersion && updateSession(player)) {
            broadcasts.playerPermissions(player.uuid.toString(), player.rank.groupId, player.rankVersion)
        }
    }

    /** Resets an expired rank to the default group, unless an admin changed it since [now] was read. */
    fun expire(
        player: Player,
        now: Instant,
    ) {
        val defaultGroup = permissionGroups.findDefault().value.id
        val version = players.resetExpiredRank(player.uuid, defaultGroup, now) ?: return publish(player.uuid)
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
