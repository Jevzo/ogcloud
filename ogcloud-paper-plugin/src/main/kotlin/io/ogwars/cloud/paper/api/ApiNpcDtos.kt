package io.ogwars.cloud.paper.api

import io.ogwars.cloud.common.model.NpcClickAction
import io.ogwars.cloud.common.model.NpcClickActionType
import io.ogwars.cloud.common.model.NpcDefinition
import io.ogwars.cloud.common.model.NpcLocation
import io.ogwars.cloud.common.model.NpcLookAtConfig
import io.ogwars.cloud.common.model.NpcModel
import io.ogwars.cloud.common.model.NpcSkin
import io.ogwars.cloud.common.model.NpcTransferStrategy

data class ApiNpcResponse(
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

data class ApiNpcTransferActionRequest(
    val group: String,
    val routingStrategy: NpcTransferStrategy? = null,
)

data class ApiNpcActionRequest(
    val type: NpcClickActionType = NpcClickActionType.NONE,
    val transfer: ApiNpcTransferActionRequest? = null,
)

data class ApiCreateNpcRequest(
    val id: String,
    val group: String,
    val location: NpcLocation,
    val title: String? = null,
    val subtitle: String? = null,
    val model: NpcModel = NpcModel.STEVE,
    val skin: NpcSkin? = null,
    val lookAt: NpcLookAtConfig = NpcLookAtConfig(),
    val leftAction: ApiNpcActionRequest = ApiNpcActionRequest(),
    val rightAction: ApiNpcActionRequest = ApiNpcActionRequest(),
)

data class ApiUpdateNpcRequest(
    val location: NpcLocation? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val model: NpcModel? = null,
    val skin: NpcSkin? = null,
    val clearSkin: Boolean = false,
    val lookAt: NpcLookAtConfig? = null,
    val leftAction: ApiNpcActionRequest? = null,
    val rightAction: ApiNpcActionRequest? = null,
)

fun ApiNpcResponse.toDefinition(): NpcDefinition =
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
