package io.ogwars.cloud.common.event

data class ServerStopEvent(
    val serverId: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
)
