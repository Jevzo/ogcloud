package io.ogwars.cloud.common.model

import java.time.Instant

data class PlayerDocument(
    val id: String,
    val name: String,
    val permission: PermissionConfig = PermissionConfig(),
    val firstJoin: Instant,
)
