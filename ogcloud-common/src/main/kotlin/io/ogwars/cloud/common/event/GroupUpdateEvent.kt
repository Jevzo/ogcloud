package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.GroupType

data class GroupUpdateEvent(
    val groupId: String,
    val type: GroupType,
    val maintenance: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)
