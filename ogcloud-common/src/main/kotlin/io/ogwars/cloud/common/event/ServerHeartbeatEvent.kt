package io.ogwars.cloud.common.event

data class ServerHeartbeatEvent(
    val serverId: String,
    val group: String,
    val podIp: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val tps: Double,
    val memoryUsedMb: Long,
    val gameState: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
