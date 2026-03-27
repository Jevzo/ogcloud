package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.NpcDocument
import io.ogwars.cloud.common.model.NpcClickAction
import io.ogwars.cloud.common.model.NpcClickActionType
import io.ogwars.cloud.common.model.NpcDefinition
import io.ogwars.cloud.common.model.NpcLocation
import io.ogwars.cloud.common.model.NpcLookAtConfig
import io.ogwars.cloud.common.model.NpcModel
import io.ogwars.cloud.common.model.NpcSkin
import io.ogwars.cloud.common.model.NpcTransferStrategy
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

private const val DEFAULT_LOOK_AT_RADIUS = 4.0

data class NpcResponse(
    val id: String,
    val group: String,
    val location: NpcLocation,
    val title: String? = null,
    val subtitle: String? = null,
    val model: NpcModel = NpcModel.STEVE,
    val skin: NpcSkin? = null,
    val lookAt: NpcLookAtConfig = NpcLookAtConfig(),
    val leftAction: NpcClickAction = NpcClickAction(),
    val rightAction: NpcClickAction = NpcClickAction(),
    val createdAt: String,
    val updatedAt: String,
)

data class NpcLocationRequest(
    @field:NotBlank val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

data class NpcSkinRequest(
    @field:NotBlank val textureValue: String,
    val textureSignature: String? = null,
)

data class NpcLookAtRequest(
    val enabled: Boolean = false,
    @field:PositiveOrZero val radius: Double = DEFAULT_LOOK_AT_RADIUS,
)

data class UpdateNpcLookAtRequest(
    val enabled: Boolean? = null,
    @field:PositiveOrZero val radius: Double? = null,
)

data class NpcTransferActionRequest(
    @field:NotBlank val group: String,
    val routingStrategy: NpcTransferStrategy? = null,
)

data class NpcActionRequest(
    val type: NpcClickActionType = NpcClickActionType.NONE,
    @field:Valid val transfer: NpcTransferActionRequest? = null,
)

data class CreateNpcRequest(
    @field:NotBlank val id: String,
    @field:NotBlank val group: String,
    @field:Valid val location: NpcLocationRequest,
    val title: String? = null,
    val subtitle: String? = null,
    val model: NpcModel = NpcModel.STEVE,
    @field:Valid val skin: NpcSkinRequest? = null,
    @field:Valid val lookAt: NpcLookAtRequest = NpcLookAtRequest(),
    @field:Valid val leftAction: NpcActionRequest = NpcActionRequest(),
    @field:Valid val rightAction: NpcActionRequest = NpcActionRequest(),
)

data class UpdateNpcRequest(
    @field:Valid val location: NpcLocationRequest? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val model: NpcModel? = null,
    @field:Valid val skin: NpcSkinRequest? = null,
    val clearSkin: Boolean = false,
    @field:Valid val lookAt: UpdateNpcLookAtRequest? = null,
    @field:Valid val leftAction: NpcActionRequest? = null,
    @field:Valid val rightAction: NpcActionRequest? = null,
)

fun NpcDocument.toResponse(): NpcResponse =
    NpcResponse(
        id = id,
        group = group,
        location = location,
        title = title,
        subtitle = subtitle,
        model = model,
        skin = skin,
        lookAt = lookAt,
        leftAction = leftAction,
        rightAction = rightAction,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

fun NpcResponse.toDefinition(): NpcDefinition =
    NpcDefinition(
        id = id,
        group = group,
        location = location,
        title = title,
        subtitle = subtitle,
        model = model,
        skin = skin,
        lookAt = lookAt,
        leftAction = leftAction,
        rightAction = rightAction,
    )

fun NpcLocationRequest.toModel(): NpcLocation =
    NpcLocation(
        world = world,
        x = x,
        y = y,
        z = z,
        yaw = yaw,
        pitch = pitch,
    )

fun NpcSkinRequest.toModel(): NpcSkin =
    NpcSkin(
        textureValue = textureValue,
        textureSignature = textureSignature,
    )

fun NpcLookAtRequest.toModel(): NpcLookAtConfig =
    NpcLookAtConfig(
        enabled = enabled,
        radius = radius,
    )

fun UpdateNpcLookAtRequest.merge(current: NpcLookAtConfig): NpcLookAtConfig =
    current.copy(
        enabled = enabled ?: current.enabled,
        radius = radius ?: current.radius,
    )
