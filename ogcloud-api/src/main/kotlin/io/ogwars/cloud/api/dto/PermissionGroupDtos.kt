package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.PermissionGroupDocument
import io.ogwars.cloud.common.model.DisplayConfig
import io.ogwars.cloud.common.model.PermissionGroupPermission
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

data class PermissionGroupPermissionRequest(
    @field:NotBlank val perm: String,
    val description: String = "",
)

data class AddPermissionToGroupRequest(
    @field:NotBlank val perm: String,
    val description: String = "",
)

data class PermissionGroupPermissionResponse(
    val perm: String,
    val description: String,
)

data class CreatePermissionGroupRequest(
    @field:NotBlank val id: String,
    @field:NotBlank val name: String,
    val display: DisplayConfigRequest = DisplayConfigRequest(),
    val weight: Int = 100,
    val default: Boolean = false,
    val permissions: List<@Valid PermissionGroupPermissionRequest> = emptyList(),
)

data class UpdatePermissionGroupRequest(
    val name: String? = null,
    @field:Valid val display: DisplayConfigRequest? = null,
    val weight: Int? = null,
    val default: Boolean? = null,
    val permissions: List<@Valid PermissionGroupPermissionRequest>? = null,
)

data class PermissionGroupResponse(
    val id: String,
    val name: String,
    val display: DisplayConfigResponse,
    val weight: Int,
    val default: Boolean,
    val permissions: List<PermissionGroupPermissionResponse>,
)

fun DisplayConfig.toResponse(): DisplayConfigResponse =
    DisplayConfigResponse(
        chatPrefix = chatPrefix,
        chatSuffix = chatSuffix,
        nameColor = nameColor,
        tabPrefix = tabPrefix,
    )

fun PermissionGroupPermissionRequest.toPermission(): PermissionGroupPermission =
    PermissionGroupPermission(
        perm = perm,
        description = description,
    )

fun AddPermissionToGroupRequest.toPermission(): PermissionGroupPermission =
    PermissionGroupPermission(
        perm = perm,
        description = description,
    )

fun PermissionGroupPermission.toResponse(): PermissionGroupPermissionResponse =
    PermissionGroupPermissionResponse(
        perm = perm,
        description = description,
    )

fun PermissionGroupDocument.toResponse(): PermissionGroupResponse =
    PermissionGroupResponse(
        id = id,
        name = name,
        display = display.toResponse(),
        weight = weight,
        default = default,
        permissions = permissions.map { it.toResponse() },
    )
