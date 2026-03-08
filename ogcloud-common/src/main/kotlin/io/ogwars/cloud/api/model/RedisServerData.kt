package io.ogwars.cloud.api.model

private const val UNKNOWN_TPS = -1.0

data class RedisServerData(
    val id: String,
    val group: String,
    val type: String,
    val displayName: String,
    val state: String,
    val gameState: String? = null,
    val podName: String,
    val podIp: String? = null,
    val port: Int = 25565,
    val templateVersion: String,
    val playerCount: Int = 0,
    val maxPlayers: Int = 0,
    val tps: Double = UNKNOWN_TPS,
    val memoryUsedMb: Long = 0,
    val podIpRetries: Int = 0,
    val startedAt: Long? = null,
    val lastHeartbeat: Long? = null,
    val drainingStartedAt: Long? = null,
)
