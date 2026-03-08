package io.ogwars.cloud.controller.model

import io.ogwars.cloud.api.model.DisplayConfig
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "permission_groups")
data class PermissionGroupDocument(
    @Id val id: String,
    val name: String,
    val display: DisplayConfig = DisplayConfig(),
    val weight: Int,
    val default: Boolean,
    val permissions: List<String>,
)
