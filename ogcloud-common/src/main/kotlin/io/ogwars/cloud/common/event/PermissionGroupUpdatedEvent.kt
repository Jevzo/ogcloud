package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.PermissionGroupDocument

data class PermissionGroupUpdatedEvent(
    val groupId: String,
    val group: PermissionGroupDocument? = null,
    val deleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)
