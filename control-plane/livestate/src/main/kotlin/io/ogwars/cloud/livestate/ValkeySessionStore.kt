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
            Revisioned(RecordCodec.decodeSession(data), revision)
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

    override fun remove(session: PlayerSession) {
        valkey.evalLong(
            LuaScripts.REMOVE_SESSION,
            listOf(
                ValkeyKeys.session(session.playerUuid),
                ValkeyKeys.sessionName(session.playerName),
                ValkeyKeys.ONLINE_PLAYERS,
            ),
            session.playerUuid.toString().utf8(),
        )
    }

    override fun onlinePlayers(): Set<UUID> =
        valkey
            .sync()
            .smembers(ValkeyKeys.ONLINE_PLAYERS)
            .map {
                UUID.fromString(it.utf8())
            }.toSet()
}
