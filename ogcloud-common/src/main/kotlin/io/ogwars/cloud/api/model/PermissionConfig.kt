package io.ogwars.cloud.api.model

data class PermissionConfig(
    val group: String = "default",
    val length: Long = -1,
    val endMillis: Long = -1,
)
