package io.ogwars.cloud.sdk

import java.util.Optional
import java.util.UUID

interface ServerInfo {
    val serverId: String
    val groupId: String
    val groupType: GroupType

    /** The runtime profile, e.g. paper-26.3. */
    val profileId: String
    val state: ServerState
    val gameState: GameState
    val host: String
    val port: Int
    val players: Int
    val maxPlayers: Int

    /** Players without the bypass permission cannot join while the group is in maintenance. */
    val groupMaintenance: Boolean
}

interface PlayerLocation {
    val player: UUID
    val name: String
    val proxyId: String

    /** Empty until the player reached a server. */
    val serverId: Optional<String>
}

enum class GroupType { DYNAMIC, STATIC, PROXY }

enum class ServerState { REQUESTED, PREPARING, STARTING, RUNNING, DRAINING, STOPPING, STOPPED }

enum class GameState { LOBBY, INGAME, ENDING }

enum class RoutingStrategy { LEAST_FILLED, MOST_FILLED, BALANCED, ROUND_ROBIN }
