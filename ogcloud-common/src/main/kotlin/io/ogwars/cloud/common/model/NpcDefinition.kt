package io.ogwars.cloud.common.model

data class NpcDefinition(
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
)
