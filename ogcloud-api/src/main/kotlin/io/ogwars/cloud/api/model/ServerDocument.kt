package io.ogwars.cloud.api.model

import io.ogwars.cloud.common.model.GameState
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.ServerState
import java.time.Instant

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
    val tps: Double = -1.0,
    val memoryUsedMb: Long = 0,
    val podIpRetries: Int = 0,
    val startedAt: Instant? = null,
    val lastHeartbeat: Instant? = null,
)
