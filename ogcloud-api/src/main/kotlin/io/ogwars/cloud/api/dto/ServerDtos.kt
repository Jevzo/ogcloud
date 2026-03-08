package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.api.model.ServerState
import jakarta.validation.constraints.NotBlank

data class ServerRequestBody(
    @field:NotBlank val group: String,
)

data class ServerRequestResponse(
    val serverId: String,
    val group: String,
)

data class ServerResponse(
    val id: String,
    val group: String,
    val type: GroupType,
    val displayName: String,
    val state: ServerState,
    val gameState: String?,
    val podName: String,
    val podIp: String?,
    val port: Int,
    val templateVersion: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val tps: Double,
    val memoryUsedMb: Long,
    val startedAt: String?,
    val lastHeartbeat: String?,
)

fun ServerDocument.toResponse(): ServerResponse =
    ServerResponse(
        id = id,
        group = group,
        type = type,
        displayName = displayName,
        state = state,
        gameState = gameState?.name,
        podName = podName,
        podIp = podIp,
        port = port,
        templateVersion = templateVersion,
        playerCount = playerCount,
        maxPlayers = maxPlayers,
        tps = tps,
        memoryUsedMb = memoryUsedMb,
        startedAt = startedAt?.toString(),
        lastHeartbeat = lastHeartbeat?.toString(),
    )
