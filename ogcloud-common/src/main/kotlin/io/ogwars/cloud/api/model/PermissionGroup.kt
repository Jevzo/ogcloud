package io.ogwars.cloud.api.model

data class PermissionGroup(
    val id: String,
    val name: String,
    val display: DisplayConfig,
    val weight: Int,
    val permissions: List<String>,
)
