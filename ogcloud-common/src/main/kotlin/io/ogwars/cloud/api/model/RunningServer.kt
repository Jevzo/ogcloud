package io.ogwars.cloud.api.model

data class RunningServer(
    val id: String,
    val group: String,
    val type: GroupType,
    val displayName: String,
    val state: ServerState,
    val gameState: GameState? = null,
    val address: String? = null,
    val playerCount: Int = 0,
    val maxPlayers: Int = 0,
)

fun RedisServerData.toRunningServer(): RunningServer =
    RunningServer(
        id = id,
        group = group,
        type = GroupType.valueOf(type),
        displayName = displayName,
        state = ServerState.valueOf(state),
        gameState = gameState?.let(GameState::valueOf),
        address = podIp?.let { "$it:$port" },
        playerCount = playerCount,
        maxPlayers = maxPlayers,
    )
