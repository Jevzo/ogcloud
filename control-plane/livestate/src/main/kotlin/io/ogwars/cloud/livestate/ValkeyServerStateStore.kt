package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.ServerStateStore
import java.util.UUID

class ValkeyServerStateStore(
    private val valkey: ValkeyConnection,
) : ServerStateStore {
    override fun create(server: Server): Boolean =
        valkey.evalLong(
            LuaScripts.CREATE,
            listOf(ValkeyKeys.server(server.id), ValkeyKeys.SERVERS, ValkeyKeys.serversOfGroup(server.groupId)),
            RecordCodec.encode(server),
            ValkeyKeys.SERVER_TTL.millisArg(),
            server.id.toString().utf8(),
        ) == 1L

    override fun get(id: UUID): Revisioned<Server>? =
        valkey.readRecords(listOf(ValkeyKeys.server(id))).single()?.let(::toServer)

    override fun listAll(): List<Revisioned<Server>> = list(ValkeyKeys.SERVERS)

    override fun listByGroup(groupId: GroupId): List<Revisioned<Server>> = list(ValkeyKeys.serversOfGroup(groupId))

    override fun compareAndSet(
        expectedRevision: Long,
        updated: Server,
    ): Boolean =
        valkey.evalLong(
            LuaScripts.COMPARE_AND_SET,
            listOf(ValkeyKeys.server(updated.id)),
            expectedRevision.toString().utf8(),
            RecordCodec.encode(updated),
            ValkeyKeys.SERVER_TTL.millisArg(),
        ) == 1L

    override fun delete(server: Server) {
        val member = server.id.toString().utf8()
        valkey.sync().del(ValkeyKeys.server(server.id))
        valkey.sync().srem(ValkeyKeys.SERVERS, member)
        valkey.sync().srem(ValkeyKeys.serversOfGroup(server.groupId), member)
    }

    private fun list(setKey: String): List<Revisioned<Server>> {
        val ids = valkey.sync().smembers(setKey).map { UUID.fromString(it.utf8()) }
        val records = valkey.readRecords(ids.map(ValkeyKeys::server))
        // Ids whose record expired are dropped from the index on the way.
        ids.zip(records).filter { it.second == null }.forEach { (id, _) ->
            valkey.sync().srem(setKey, id.toString().utf8())
        }
        return records.filterNotNull().map(::toServer)
    }

    private fun toServer(record: Pair<Long, ByteArray>) =
        Revisioned(RecordCodec.decodeServer(record.second), record.first)
}
