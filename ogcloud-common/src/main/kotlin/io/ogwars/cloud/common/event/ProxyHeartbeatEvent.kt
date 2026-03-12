package io.ogwars.cloud.common.event

data class ProxyHeartbeatEvent(
    val proxyId: String,
    val podIp: String,
    val port: Int,
    val playerCount: Int,
    val maxPlayers: Int,
    val memoryUsedMb: Long,
    val timestamp: Long = System.currentTimeMillis(),
)
