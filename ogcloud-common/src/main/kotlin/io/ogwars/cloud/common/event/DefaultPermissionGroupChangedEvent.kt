package io.ogwars.cloud.common.event

data class DefaultPermissionGroupChangedEvent(
    val groupId: String,
    val timestamp: Long = System.currentTimeMillis(),
)
