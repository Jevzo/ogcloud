package io.ogwars.cloud.domain.server

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import java.time.Instant
import java.util.UUID

val T0: Instant = Instant.parse("2026-10-26T12:00:00Z")

fun server(
    state: ServerState = ServerState.RUNNING,
    groupType: GroupType = GroupType.DYNAMIC,
    players: Int = 0,
    since: Instant = T0,
    group: String = "lobby",
    id: UUID = UUID.fromString("0192d2c1-1a2b-7c3d-8e4f-123456789abc"),
    gameState: GameState? = if (groupType == GroupType.DYNAMIC) GameState.LOBBY else null,
    startedAt: Instant? = null,
    lastHeartbeatAt: Instant? = null,
) = Server(
    id = id,
    groupId = GroupId(group),
    groupType = groupType,
    state = state,
    stateSince = since,
    players = players,
    maxPlayers = 50,
    gameState = gameState,
    startedAt = startedAt,
    lastHeartbeatAt = lastHeartbeatAt,
)
