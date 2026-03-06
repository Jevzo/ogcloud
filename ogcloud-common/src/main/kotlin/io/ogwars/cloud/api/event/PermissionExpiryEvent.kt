package io.ogwars.cloud.api.event

data class PermissionExpiryEvent(
    val uuid: String,
    val timestamp: Long = System.currentTimeMillis()
)
