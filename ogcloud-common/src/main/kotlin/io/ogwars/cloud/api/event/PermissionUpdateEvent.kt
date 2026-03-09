package io.ogwars.cloud.api.event

import io.ogwars.cloud.api.model.DisplayConfig

data class PermissionUpdateEvent(
    val uuid: String,
    val groupId: String,
    val groupName: String,
    val permissions: List<String>,
    val display: DisplayConfig,
    val weight: Int,
    val permissionEndMillis: Long,
    val permissionVersion: Long = 0,
    val updatedBy: String,
    val timestamp: Long = System.currentTimeMillis(),
)
