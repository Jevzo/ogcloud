package io.ogwars.cloud.common.event

data class PermissionExpiryEvent(
    val uuid: String,
    val timestamp: Long = System.currentTimeMillis(),
)
