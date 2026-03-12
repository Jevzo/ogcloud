package io.ogwars.cloud.api.redis

import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.common.model.GameState
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.RedisServerData
import io.ogwars.cloud.common.model.ServerState
import java.time.Instant

fun RedisServerData.toDocument(): ServerDocument =
    ServerDocument(
        id = id,
        group = group,
        type = GroupType.valueOf(type),
        displayName = displayName,
        state = ServerState.valueOf(state),
        gameState = gameState?.let(GameState::valueOf),
        podName = podName,
        podIp = podIp,
        port = port,
        templateVersion = templateVersion,
        playerCount = playerCount,
        maxPlayers = maxPlayers,
        tps = tps,
        memoryUsedMb = memoryUsedMb,
        podIpRetries = podIpRetries,
        startedAt = startedAt?.let(Instant::ofEpochMilli),
        lastHeartbeat = lastHeartbeat?.let(Instant::ofEpochMilli),
    )
