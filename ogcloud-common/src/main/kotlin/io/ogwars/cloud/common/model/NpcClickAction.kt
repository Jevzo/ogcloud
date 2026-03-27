package io.ogwars.cloud.common.model

data class NpcClickAction(
    val type: NpcClickActionType = NpcClickActionType.NONE,
    val targetGroup: String? = null,
    val routingStrategy: NpcTransferStrategy? = null,
)

enum class NpcClickActionType {
    NONE,
    TRANSFER,
}
