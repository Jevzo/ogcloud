package io.ogwars.cloud.common.model

data class PermissionGroupDocument(
    val id: String,
    val name: String,
    val display: DisplayConfig = DisplayConfig(),
    val weight: Int,
    val default: Boolean,
    val permissions: List<String>,
)
