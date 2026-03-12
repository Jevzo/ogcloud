package io.ogwars.cloud.common.event

data class PlayerSwitchEvent(
    val uuid: String,
    val serverId: String,
    val previousServerId: String?,
    val timestamp: Long = System.currentTimeMillis(),
)
