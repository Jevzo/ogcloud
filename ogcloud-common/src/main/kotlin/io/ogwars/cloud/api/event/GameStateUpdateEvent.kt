package io.ogwars.cloud.api.event

import io.ogwars.cloud.api.model.GameState

data class GameStateUpdateEvent(
    val serverId: String,
    val group: String,
    val gameState: GameState,
    val timestamp: Long = System.currentTimeMillis(),
)
