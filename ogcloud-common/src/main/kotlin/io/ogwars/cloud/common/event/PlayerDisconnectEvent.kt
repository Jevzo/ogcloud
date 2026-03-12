package io.ogwars.cloud.common.event

data class PlayerDisconnectEvent(
    val uuid: String,
    val proxyId: String,
    val timestamp: Long = System.currentTimeMillis(),
)
