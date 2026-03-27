package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.NpcTransferStrategy

data class PlayerTransferEvent(
    val playerUuid: String? = null,
    val serverId: String? = null,
    val target: String?,
    val routingStrategy: NpcTransferStrategy? = null,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
)
