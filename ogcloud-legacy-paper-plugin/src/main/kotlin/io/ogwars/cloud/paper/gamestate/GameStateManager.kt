package io.ogwars.cloud.paper.gamestate

import io.ogwars.cloud.common.event.GameStateUpdateEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.model.GameState
import io.ogwars.cloud.paper.kafka.KafkaSendDispatcher
import com.google.gson.Gson
import java.util.logging.Logger

class GameStateManager(
    private val serverId: String,
    private val group: String,
    private val kafkaSendDispatcher: KafkaSendDispatcher,
    private val asyncHandoff: (Runnable) -> Unit,
    private val logger: Logger,
) {
    private val gson = Gson()

    @Volatile
    var currentState: GameState = GameState.LOBBY
        private set

    fun setGameState(state: GameState) {
        val previous = currentState
        currentState = state

        val updateEvent =
            GameStateUpdateEvent(
                serverId = serverId,
                group = group,
                gameState = state,
            )

        asyncHandoff(
            Runnable {
                kafkaSendDispatcher.dispatch(
                    KafkaSendDispatcher.Message(
                        topic = KafkaTopics.SERVER_GAMESTATE,
                        key = serverId,
                        payload = gson.toJson(updateEvent),
                        type = KafkaSendDispatcher.MessageType.GAME_STATE_UPDATE,
                    ),
                )
            },
        )

        logger.info("Game state changed: $previous -> $state")
    }
}
