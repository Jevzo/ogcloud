package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.ReadModelKind
import java.time.Duration
import java.util.UUID

/** The whole Valkey key layout in one place; the Go gateway mirrors it. */
object ValkeyKeys {
    const val FIELD_REVISION = "rev"
    const val FIELD_DATA = "data"

    const val SERVERS = "servers"
    const val ONLINE_PLAYERS = "players:online"

    /** Refreshed on every write; abandoned records expire and the reconciler cleans up. */
    val SERVER_TTL: Duration = Duration.ofMinutes(15)
    val SESSION_TTL: Duration = Duration.ofHours(24)

    fun server(id: UUID) = "server:$id"

    fun serversOfGroup(groupId: GroupId) = "servers:group:$groupId"

    fun session(playerUuid: UUID) = "session:$playerUuid"

    fun sessionName(name: String) = "session:name:${name.lowercase()}"

    fun lock(name: String) = "lock:$name"

    fun scalingCooldown(groupId: GroupId) = "cooldown:scaling:$groupId"

    fun crashLoop(groupId: GroupId) = "crashloop:$groupId"

    fun readModel(
        kind: ReadModelKind,
        id: String,
    ) = "readmodel:${kind.key}:$id"
}
