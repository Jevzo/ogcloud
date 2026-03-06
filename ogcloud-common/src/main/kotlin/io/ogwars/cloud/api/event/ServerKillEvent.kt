package io.ogwars.cloud.api.event

data class ServerKillEvent(
    val serverId: String,
    val reason: String = "api-kill",
    val timestamp: Long = System.currentTimeMillis()
)
