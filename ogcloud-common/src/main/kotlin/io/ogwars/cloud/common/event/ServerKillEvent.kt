package io.ogwars.cloud.common.event

data class ServerKillEvent(
    val serverId: String,
    val reason: String = "api-kill",
    val timestamp: Long = System.currentTimeMillis(),
)
