package io.ogwars.cloud.api.live

import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.server.GameState
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.session.PlayerSession
import java.time.Instant
import java.util.UUID

data class ServerView(
    val id: UUID,
    val groupId: String,
    val groupType: GroupType,
    val profileId: String,
    val state: ServerState,
    val stateSince: Instant,
    val players: Int,
    val maxPlayers: Int,
    val gameState: GameState?,
    val startedAt: Instant?,
    val lastHeartbeatAt: Instant?,
)

fun Server.toView() =
    ServerView(
        id,
        groupId.value,
        groupType,
        profileId,
        state,
        stateSince,
        players,
        maxPlayers,
        gameState,
        startedAt,
        lastHeartbeatAt,
    )

data class RequestServerRequest(
    val groupId: String,
)

data class RequestedServer(
    val serverId: UUID,
)

data class SessionView(
    val proxyId: UUID,
    /** Null until the player reaches a backend. */
    val serverId: UUID?,
    val permissionGroupId: String,
    val connectedAt: Instant,
)

data class OnlinePlayerView(
    val uuid: UUID,
    val name: String,
    val session: SessionView,
)

fun PlayerSession.toView() =
    OnlinePlayerView(playerUuid, playerName, SessionView(proxyId, serverId, permissionGroupId, connectedAt))

data class PlayerView(
    val uuid: UUID,
    val name: String,
    val permissionGroupId: String,
    /** Null for a permanent rank. */
    val rankExpiresAt: Instant?,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    /** Present while the player is online. */
    val session: SessionView?,
)

fun Player.toView(session: PlayerSession?) =
    PlayerView(uuid, name, rank.groupId, rank.expiresAt, firstSeenAt, lastSeenAt, session?.toView()?.session)

/** Exactly one destination: a server, a group (optionally with a routing strategy), or a kick message. */
data class TransferRequest(
    val serverId: UUID?,
    val groupId: String?,
    val routingStrategy: RoutingStrategy?,
    val kickMessage: String?,
)

enum class CommandTarget {
    ALL,
    SERVER,
    GROUP,
}

data class ConsoleCommandRequest(
    val target: CommandTarget,
    /** The server or group id; absent for [CommandTarget.ALL]. */
    val id: String?,
    val command: String,
)

data class PlayerSummary(
    val uuid: UUID,
    val name: String,
    val online: Boolean,
)

/** A section is null when the caller may not read that area (D-0101). */
data class SearchResult(
    val groups: List<String>?,
    val servers: List<ServerView>?,
    val players: List<PlayerSummary>?,
)

data class CrashLoopView(
    /** Failures inside the controller's window, oldest first. */
    val failures: List<Instant>,
    /** Latched until resumed; the group starts no servers meanwhile. */
    val paused: Boolean,
)
