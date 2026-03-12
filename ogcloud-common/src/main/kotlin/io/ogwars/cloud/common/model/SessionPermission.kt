package io.ogwars.cloud.common.model

data class SessionPermission(
    val group: String = "default",
    val endMillis: Long = -1,
    val version: Long = -1,
)
