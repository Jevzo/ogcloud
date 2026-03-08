package io.ogwars.cloud.api.event

import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerState

data class ServerLifecycleEvent(
    val serverId: String,
    val group: String,
    val type: GroupType,
    val state: ServerState,
    val displayName: String? = null,
    val podName: String? = null,
    val podIp: String? = null,
    val port: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
