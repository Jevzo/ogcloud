package io.ogwars.cloud.domain.scaling

import io.ogwars.cloud.domain.group.GroupId
import java.time.Instant
import java.util.UUID

enum class ScalingAction {
    START,
    DRAIN,
    UNDRAIN,
    STOP,
}

data class ScalingLogEntry(
    val id: UUID,
    val occurredAt: Instant,
    val groupId: GroupId,
    val action: ScalingAction,
    val serverId: UUID?,
    val reason: ScalingReason,
    val details: String = "",
)
