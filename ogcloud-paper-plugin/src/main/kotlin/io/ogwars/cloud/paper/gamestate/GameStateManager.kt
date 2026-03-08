package io.ogwars.cloud.paper.gamestate

import com.google.gson.Gson
import io.ogwars.cloud.api.event.GameStateUpdateEvent
import io.ogwars.cloud.api.model.GameState
import io.ogwars.cloud.paper.kafka.KafkaSendDispatcher
import java.util.logging.Logger

class GameStateManager(
    private val serverId: String,
    private val group: String,
    private val kafkaSendDispatcher: KafkaSendDispatcher,
    private val asyncHandoff: (Runnable) -> Unit,
    private val logger: Logger
) {

    private val gson = Gson()

    @Volatile
    var currentState: GameState = GameState.LOBBY
        private set

    fun setGameState(state: GameState) {
        val previous = currentState
        currentState = state

        val updateEvent = GameStateUpdateEvent(
            serverId = serverId, group = group, gameState = state
        )

        asyncHandoff(
            Runnable {
                kafkaSendDispatcher.dispatch(
                    KafkaSendDispatcher.Message(
                        topic = TOPIC,
                        key = serverId,
                        payload = gson.toJson(updateEvent),
                        type = KafkaSendDispatcher.MessageType.GAME_STATE_UPDATE
                    )
                )
            })

        logger.info("Game state changed: $previous -> $state")
    }

    companion object {
        private const val TOPIC = "ogcloud.server.gamestate"
    }
}
