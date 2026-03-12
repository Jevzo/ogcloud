package io.ogwars.cloud.common.model

import java.util.UUID

data class PlayerInfo(
    val uuid: UUID,
    val name: String,
    val serverId: String? = null,
    val proxyId: String? = null,
    val groupName: String? = null,
    val permissions: List<String> = emptyList(),
)
