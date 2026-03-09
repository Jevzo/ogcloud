package io.ogwars.cloud.api.event

import io.ogwars.cloud.api.model.PermissionGroupDocument

data class PermissionGroupUpdatedEvent(
    val groupId: String,
    val group: PermissionGroupDocument? = null,
    val deleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)
