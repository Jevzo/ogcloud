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

    const val SERVER_PREFIX = "server:"

    fun server(id: UUID) = "$SERVER_PREFIX$id"

    fun serversOfGroup(groupId: GroupId) = "servers:group:$groupId"

    fun session(playerUuid: UUID) = "session:$playerUuid"

    fun sessionName(name: String) = "session:name:${name.lowercase()}"

    fun lock(name: String) = "lock:$name"

    fun scalingCooldown(groupId: GroupId) = "cooldown:scaling:$groupId"

    fun crashLoop(groupId: GroupId) = "crashloop:$groupId"

    /** Latched pause; no ttl, only an admin resume clears it. */
    fun crashLoopPaused(groupId: GroupId) = "crashloop:paused:$groupId"

    /** Spring Session's namespace for the API's login sessions (D-0091). */
    const val WEB_SESSION_NAMESPACE = "websession"

    /** A rotated refresh token, by SHA-256 hex; it expires with the token (D-0090). */
    fun rotatedRefreshToken(tokenHash: String) = "oauth:rotated:$tokenHash"

    /** A token bucket per limited class of request and client address (D-0086). */
    fun rateLimit(
        bucket: String,
        client: String,
    ) = "ratelimit:$bucket:$client"

    fun readModel(
        kind: ReadModelKind,
        id: String,
    ) = "readmodel:${kind.key}:$id"
}
