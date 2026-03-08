package io.ogwars.cloud.api.event

data class ServerHeartbeatEvent(
    val serverId: String,
    val group: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val tps: Double,
    val memoryUsedMb: Long,
    val gameState: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
