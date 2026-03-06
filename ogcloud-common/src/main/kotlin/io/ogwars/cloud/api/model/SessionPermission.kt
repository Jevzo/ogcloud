package io.ogwars.cloud.api.model

data class SessionPermission(
    val group: String = "default",
    val endMillis: Long = -1
)
