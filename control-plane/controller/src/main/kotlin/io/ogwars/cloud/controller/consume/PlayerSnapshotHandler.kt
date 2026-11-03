package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.ProxyPlayersSnapshot
import io.ogwars.cloud.contracts.toInstant
import io.ogwars.cloud.controller.login.LoginResolver
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.messaging.toUuid
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * `ogcloud.player.snapshot`: a proxy's online players every 60 seconds (D-0130). A player it holds without a
 * session gets one back; a session of that proxy for a player it no longer lists is dropped, if it is older than
 * the snapshot, so a lost join or leave heals within a minute.
 */
@Component
class PlayerSnapshotHandler(
    private val sessions: SessionStore,
    private val logins: LoginResolver,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(snapshot: ProxyPlayersSnapshot) {
        val proxy = snapshot.proxyId.toUuid("proxy_id")
        val takenAt = snapshot.metadata.occurredAt.toInstant()
        val listed = snapshot.playersList.associateBy { it.playerUuid.toUuid("player_uuid") }
        listed.forEach { (player, entry) ->
            if (sessions.get(player) == null) {
                log.info("Restoring the lost session of {} on proxy {}", player, proxy)
                logins.restore(
                    player,
                    entry.playerName,
                    proxy,
                    entry.serverId.takeIf(String::isNotEmpty)?.toUuid("server_id"),
                    takenAt,
                )
            }
        }
        var after: UUID? = null
        do {
            val page = sessions.listOnline(after, PAGE_SIZE)
            page
                .filter { it.proxyId == proxy && it.playerUuid !in listed && it.connectedAt.isBefore(takenAt) }
                .forEach { stale ->
                    val current = sessions.get(stale.playerUuid) ?: return@forEach
                    // Removal is conditional on the revision read, so a reconnect in between survives (D-0069).
                    if (current.value.proxyId == proxy && sessions.remove(current)) {
                        log.info("Dropped the stale session of {} on proxy {}", stale.playerUuid, proxy)
                    }
                }
            after = page.lastOrNull()?.playerUuid
        } while (page.size == PAGE_SIZE)
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
