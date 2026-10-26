package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.ServerStateStore
import org.slf4j.LoggerFactory
import java.util.UUID

class ValkeyServerStateStore(
    private val valkey: ValkeyConnection,
) : ServerStateStore {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun create(server: Server): Boolean =
        valkey.evalLong(
            LuaScripts.CREATE,
            listOf(ValkeyKeys.server(server.id), ValkeyKeys.SERVERS, ValkeyKeys.serversOfGroup(server.groupId)),
            RecordCodec.encode(server),
            ValkeyKeys.SERVER_TTL.millisArg(),
            server.id.toString().utf8(),
        ) == 1L

    override fun get(id: UUID): Revisioned<Server>? =
        valkey.readRecords(listOf(ValkeyKeys.server(id))).single()?.let { decode(ValkeyKeys.server(id), it) }

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
        // One unreadable record must not stall every listing (autoscaler, reconciler); it is reported and skipped.
        return ids.zip(records).mapNotNull { (id, record) ->
            record?.let {
                runCatching { decode(ValkeyKeys.server(id), it) }
                    .onFailure { error ->
                        log.error("Skipping unreadable server record {}", ValkeyKeys.server(id), error)
                    }.getOrNull()
            }
        }
    }

    private fun decode(
        key: String,
        record: Pair<Long, ByteArray>,
    ) = Revisioned(RecordCodec.decodeServer(key, record.second), record.first)
}
