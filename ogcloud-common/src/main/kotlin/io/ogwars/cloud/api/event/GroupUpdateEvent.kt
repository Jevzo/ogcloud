package io.ogwars.cloud.api.event

import io.ogwars.cloud.api.model.GroupType

data class GroupUpdateEvent(
    val groupId: String,
    val type: GroupType,
    val maintenance: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
