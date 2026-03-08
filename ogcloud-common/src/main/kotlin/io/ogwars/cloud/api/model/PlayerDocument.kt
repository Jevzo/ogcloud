package io.ogwars.cloud.api.model

import java.time.Instant

data class PlayerDocument(
    val id: String,
    val name: String,
    val permission: PermissionConfig = PermissionConfig(),
    val firstJoin: Instant,
)
