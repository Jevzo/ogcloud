package io.ogwars.cloud.api.event

data class ServerRequestEvent(
    val group: String,
    val requestedBy: String,
    val serverId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
