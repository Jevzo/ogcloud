package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.DisplayConfig
import io.ogwars.cloud.api.model.PermissionGroupDocument
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

data class DisplayConfigRequest(
    val chatPrefix: String = "",
    val chatSuffix: String = "",
    val nameColor: String = "&7",
    val tabPrefix: String = "&7",
)

data class DisplayConfigResponse(
    val chatPrefix: String,
    val chatSuffix: String,
    val nameColor: String,
    val tabPrefix: String,
)

data class CreatePermissionGroupRequest(
    @field:NotBlank val id: String,
    @field:NotBlank val name: String,
    val display: DisplayConfigRequest = DisplayConfigRequest(),
    val weight: Int = 100,
    val default: Boolean = false,
    val permissions: List<String> = emptyList(),
)

data class UpdatePermissionGroupRequest(
    val name: String? = null,
    @field:Valid val display: DisplayConfigRequest? = null,
    val weight: Int? = null,
    val default: Boolean? = null,
    val permissions: List<String>? = null,
)

data class PermissionGroupResponse(
    val id: String,
    val name: String,
    val display: DisplayConfigResponse,
    val weight: Int,
    val default: Boolean,
    val permissions: List<String>,
)

fun DisplayConfig.toResponse(): DisplayConfigResponse =
    DisplayConfigResponse(
        chatPrefix = chatPrefix,
        chatSuffix = chatSuffix,
        nameColor = nameColor,
        tabPrefix = tabPrefix,
    )

fun PermissionGroupDocument.toResponse(): PermissionGroupResponse =
    PermissionGroupResponse(
        id = id,
        name = name,
        display = display.toResponse(),
        weight = weight,
        default = default,
        permissions = permissions,
    )
