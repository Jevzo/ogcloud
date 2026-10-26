package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.SessionStore
import java.util.UUID

class ValkeySessionStore(
    private val valkey: ValkeyConnection,
) : SessionStore {
    override fun put(session: PlayerSession) {
        valkey.evalLong(
            LuaScripts.PUT_SESSION,
            listOf(
                ValkeyKeys.session(session.playerUuid),
                ValkeyKeys.sessionName(session.playerName),
                ValkeyKeys.ONLINE_PLAYERS,
            ),
            RecordCodec.encode(session),
            ValkeyKeys.SESSION_TTL.millisArg(),
            session.playerUuid.toString().utf8(),
        )
    }

    override fun get(playerUuid: UUID): Revisioned<PlayerSession>? =
        valkey.readRecords(listOf(ValkeyKeys.session(playerUuid))).single()?.let { (revision, data) ->
            Revisioned(RecordCodec.decodeSession(ValkeyKeys.session(playerUuid), data), revision)
        }

    override fun findByName(name: String): Revisioned<PlayerSession>? =
        valkey
            .sync()
            .get(ValkeyKeys.sessionName(name))
            ?.let { get(UUID.fromString(it.utf8())) }

    override fun compareAndSet(
        expectedRevision: Long,
        updated: PlayerSession,
    ): Boolean =
        valkey.evalLong(
            LuaScripts.COMPARE_AND_SET,
            listOf(ValkeyKeys.session(updated.playerUuid)),
            expectedRevision.toString().utf8(),
            RecordCodec.encode(updated),
            ValkeyKeys.SESSION_TTL.millisArg(),
        ) == 1L

    override fun remove(session: Revisioned<PlayerSession>): Boolean {
        val player = session.value
        return valkey.evalLong(
            LuaScripts.REMOVE_SESSION,
            listOf(
                ValkeyKeys.session(player.playerUuid),
                ValkeyKeys.sessionName(player.playerName),
                ValkeyKeys.ONLINE_PLAYERS,
            ),
            session.revision.toString().utf8(),
            player.playerUuid.toString().utf8(),
        ) == 1L
    }

    /** Players whose session expired without a disconnect (e.g. a crashed proxy) are pruned on the way. */
    override fun onlinePlayers(): Set<UUID> {
        val players = valkey.sync().smembers(ValkeyKeys.ONLINE_PLAYERS).map { UUID.fromString(it.utf8()) }
        val sessions = valkey.readRecords(players.map(ValkeyKeys::session))
        val (live, expired) = players.zip(sessions).partition { it.second != null }
        expired.forEach { (uuid, _) -> valkey.sync().srem(ValkeyKeys.ONLINE_PLAYERS, uuid.toString().utf8()) }
        return live.map { it.first }.toSet()
    }
}
