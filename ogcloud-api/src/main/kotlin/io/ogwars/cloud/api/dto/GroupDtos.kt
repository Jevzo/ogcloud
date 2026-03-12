package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.GroupDocument
import io.ogwars.cloud.api.model.ResourceConfig
import io.ogwars.cloud.api.model.ScalingConfig
import io.ogwars.cloud.common.model.GroupType
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CreateGroupRequest(
    @field:NotBlank val id: String,
    val type: GroupType,
    @field:NotBlank val templateBucket: String,
    @field:NotBlank val templatePath: String,
    @field:NotBlank val templateVersion: String,
    @field:Valid val scaling: ScalingConfigDto,
    @field:Valid val resources: ResourceConfigDto,
    @field:NotBlank val jvmFlags: String,
    @field:Min(1) val drainTimeoutSeconds: Int,
    @field:NotBlank val serverImage: String,
    val storageSize: String = "5Gi",
)

data class ScalingConfigDto(
    @field:Min(0) val minOnline: Int,
    @field:Min(1) val maxInstances: Int,
    @field:Min(1) val playersPerServer: Int,
    val scaleUpThreshold: Double,
    val scaleDownThreshold: Double,
    @field:Min(1) val cooldownSeconds: Int,
)

data class ResourceConfigDto(
    @field:NotBlank val memoryRequest: String,
    @field:NotBlank val memoryLimit: String,
    @field:NotBlank val cpuRequest: String,
    @field:NotBlank val cpuLimit: String,
)

data class UpdateGroupRequest(
    val templateBucket: String? = null,
    val templatePath: String? = null,
    val templateVersion: String? = null,
    @field:Valid val scaling: ScalingConfigDto? = null,
    @field:Valid val resources: ResourceConfigDto? = null,
    val jvmFlags: String? = null,
    @field:Min(1) val drainTimeoutSeconds: Int? = null,
    val serverImage: String? = null,
    val storageSize: String? = null,
)

data class GroupResponse(
    val id: String,
    val type: GroupType,
    val templateBucket: String,
    val templatePath: String,
    val templateVersion: String,
    val scaling: ScalingConfig,
    val resources: ResourceConfig,
    val jvmFlags: String,
    val drainTimeoutSeconds: Int,
    val serverImage: String,
    val storageSize: String,
    val maintenance: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

fun GroupDocument.toResponse(): GroupResponse =
    GroupResponse(
        id = id,
        type = type,
        templateBucket = templateBucket,
        templatePath = templatePath,
        templateVersion = templateVersion,
        scaling = scaling,
        resources = resources,
        jvmFlags = jvmFlags,
        drainTimeoutSeconds = drainTimeoutSeconds,
        serverImage = serverImage,
        storageSize = storageSize,
        maintenance = maintenance,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

fun CreateGroupRequest.toDocument(now: Instant = Instant.now()): GroupDocument =
    GroupDocument(
        id = id,
        type = type,
        templateBucket = templateBucket,
        templatePath = templatePath,
        templateVersion = templateVersion,
        scaling = scaling.toModel(),
        resources = resources.toModel(),
        jvmFlags = jvmFlags,
        drainTimeoutSeconds = drainTimeoutSeconds,
        serverImage = serverImage,
        storageSize = storageSize,
        createdAt = now,
        updatedAt = now,
    )

fun ScalingConfigDto.toModel(): ScalingConfig =
    ScalingConfig(
        minOnline = minOnline,
        maxInstances = maxInstances,
        playersPerServer = playersPerServer,
        scaleUpThreshold = scaleUpThreshold,
        scaleDownThreshold = scaleDownThreshold,
        cooldownSeconds = cooldownSeconds,
    )

fun ResourceConfigDto.toModel(): ResourceConfig =
    ResourceConfig(
        memoryRequest = memoryRequest,
        memoryLimit = memoryLimit,
        cpuRequest = cpuRequest,
        cpuLimit = cpuLimit,
    )
