package io.ogwars.cloud.common.model

data class RedisPlayerSession(
    val name: String,
    val proxyId: String,
    val connectedAt: Long,
    val serverId: String? = null,
    val permission: SessionPermission = SessionPermission(),
    val display: DisplayConfig = DisplayConfig(),
    val weight: Int = 100,
    val permissions: List<String> = emptyList(),
)
