package io.ogwars.cloud.paper.gamestate

import io.ogwars.cloud.api.event.GameStateUpdateEvent
import io.ogwars.cloud.api.model.GameState
import io.ogwars.cloud.paper.kafka.KafkaManager
import java.util.logging.Logger

class GameStateManager(
    private val serverId: String,
    private val group: String,
    private val kafkaManager: KafkaManager,
    private val logger: Logger
) {

    @Volatile
    var currentState: GameState = GameState.LOBBY
        private set

    fun setGameState(state: GameState) {
        val previous = currentState
        currentState = state

        kafkaManager.send(
            TOPIC, serverId, GameStateUpdateEvent(
                serverId = serverId,
                group = group,
                gameState = state
            )
        )

        logger.info("Game state changed: $previous -> $state")
    }

    companion object {
        private const val TOPIC = "ogcloud.server.gamestate"
    }
}
