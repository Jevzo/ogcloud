package io.ogwars.cloud.api.event

data class PlayerConnectEvent(
    val uuid: String,
    val name: String,
    val proxyId: String,
    val timestamp: Long = System.currentTimeMillis(),
)
