package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.GameState

data class GameStateUpdateEvent(
    val serverId: String,
    val group: String,
    val gameState: GameState,
    val timestamp: Long = System.currentTimeMillis(),
)
