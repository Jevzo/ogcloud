package io.ogwars.cloud.controller.model

import io.ogwars.cloud.api.model.GameState
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerState
import java.time.Instant

private const val UNKNOWN_TPS = -1.0

data class ServerDocument(
    val id: String,
    val group: String,
    val type: GroupType,
    val displayName: String,
    val state: ServerState,
    val gameState: GameState? = null,
    val podName: String,
    val podIp: String? = null,
    val port: Int = 25565,
    val templateVersion: String,
    val playerCount: Int = 0,
    val maxPlayers: Int = 0,
    val tps: Double = UNKNOWN_TPS,
    val memoryUsedMb: Long = 0,
    val podIpRetries: Int = 0,
    val startedAt: Instant? = null,
    val lastHeartbeat: Instant? = null,
    val drainingStartedAt: Instant? = null
)
