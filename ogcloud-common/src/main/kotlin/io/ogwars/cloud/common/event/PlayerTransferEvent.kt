package io.ogwars.cloud.common.event

data class PlayerTransferEvent(
    val playerUuid: String? = null,
    val serverId: String? = null,
    val target: String?,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
)
