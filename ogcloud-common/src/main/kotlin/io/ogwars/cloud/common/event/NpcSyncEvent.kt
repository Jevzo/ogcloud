package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.NpcDefinition

data class NpcSyncEvent(
    val operation: NpcSyncOperation,
    val npc: NpcDefinition? = null,
    val npcId: String? = null,
    val group: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class NpcSyncOperation {
    UPSERT,
    DELETE,
}
