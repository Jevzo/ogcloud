package io.ogwars.cloud.api.event

data class DefaultPermissionGroupChangedEvent(
    val groupId: String,
    val timestamp: Long = System.currentTimeMillis(),
)
