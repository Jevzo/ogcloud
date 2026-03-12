package io.ogwars.cloud.controller.redis

import io.ogwars.cloud.common.model.GameState
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.RedisServerData
import io.ogwars.cloud.common.model.ServerState
import io.ogwars.cloud.controller.model.ServerDocument
import java.time.Instant

fun ServerDocument.toRedisData(): RedisServerData =
    RedisServerData(
        id = id,
        group = group,
        type = type.name,
        displayName = displayName,
        state = state.name,
        gameState = gameState?.name,
        podName = podName,
        podIp = podIp,
        port = port,
        templateVersion = templateVersion,
        playerCount = playerCount,
        maxPlayers = maxPlayers,
        tps = tps,
        memoryUsedMb = memoryUsedMb,
        podIpRetries = podIpRetries,
        startedAt = startedAt?.toEpochMilli(),
        lastHeartbeat = lastHeartbeat?.toEpochMilli(),
        drainingStartedAt = drainingStartedAt?.toEpochMilli(),
    )

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
        drainingStartedAt = drainingStartedAt?.let(Instant::ofEpochMilli),
    )
