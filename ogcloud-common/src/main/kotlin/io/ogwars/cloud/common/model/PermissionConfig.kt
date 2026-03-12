package io.ogwars.cloud.common.model

data class PermissionConfig(
    val group: String = "default",
    val length: Long = -1,
    val endMillis: Long = -1,
    val version: Long = 0,
)
