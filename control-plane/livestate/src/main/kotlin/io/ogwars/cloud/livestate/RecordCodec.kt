package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.GameState
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.session.PlayerSession
import com.google.protobuf.Timestamp
import ogcloud.events.v1.Common
import ogcloud.internal.v1.Livestate.ServerRecord
import java.time.Instant
import java.util.UUID
import ogcloud.internal.v1.Livestate.PlayerSession as PlayerSessionRecord

/** Maps domain objects to the Protobuf records shared with the Go gateway. */
internal object RecordCodec {
    fun encode(server: Server): ByteArray =
        ServerRecord
            .newBuilder()
            .setServerId(server.id.toString())
            .setGroupId(server.groupId.value)
            .setGroupType(Common.GroupType.valueOf("GROUP_TYPE_${server.groupType.name}"))
            .setProfileId(server.profileId)
            .setState(Common.ServerState.valueOf("SERVER_STATE_${server.state.name}"))
            .setStateSince(server.stateSince.toTimestamp())
            .setGameState(
                server.gameState?.let { Common.GameState.valueOf("GAME_STATE_${it.name}") }
                    ?: Common.GameState.GAME_STATE_UNSPECIFIED,
            ).setHost(server.host.orEmpty())
            .setPort(server.port ?: 0)
            .setPlayers(server.players)
            .setMaxPlayers(server.maxPlayers)
            .apply { server.startedAt?.let { setStartedAt(it.toTimestamp()) } }
            .apply { server.lastHeartbeatAt?.let { setLastHeartbeatAt(it.toTimestamp()) } }
            .build()
            .toByteArray()

    fun decodeServer(bytes: ByteArray): Server {
        val record = ServerRecord.parseFrom(bytes)
        return Server(
            id = UUID.fromString(record.serverId),
            groupId = GroupId(record.groupId),
            groupType = GroupType.valueOf(record.groupType.name.removePrefix("GROUP_TYPE_")),
            profileId = record.profileId,
            state = ServerState.valueOf(record.state.name.removePrefix("SERVER_STATE_")),
            stateSince = record.stateSince.toInstant(),
            players = record.players,
            maxPlayers = record.maxPlayers,
            gameState =
                record.gameState
                    .takeUnless { it == Common.GameState.GAME_STATE_UNSPECIFIED }
                    ?.let { GameState.valueOf(it.name.removePrefix("GAME_STATE_")) },
            startedAt = if (record.hasStartedAt()) record.startedAt.toInstant() else null,
            lastHeartbeatAt = if (record.hasLastHeartbeatAt()) record.lastHeartbeatAt.toInstant() else null,
            host = record.host.ifEmpty { null },
            port = record.port.takeIf { it != 0 },
        )
    }

    fun encode(session: PlayerSession): ByteArray =
        PlayerSessionRecord
            .newBuilder()
            .setPlayerUuid(session.playerUuid.toString())
            .setPlayerName(session.playerName)
            .setProxyId(session.proxyId.toString())
            .setServerId(session.serverId?.toString().orEmpty())
            .setPermissionGroupId(session.permissionGroupId)
            .setRankVersion(session.rankVersion)
            .setConnectedAt(session.connectedAt.toTimestamp())
            .build()
            .toByteArray()

    fun decodeSession(bytes: ByteArray): PlayerSession {
        val record = PlayerSessionRecord.parseFrom(bytes)
        return PlayerSession(
            playerUuid = UUID.fromString(record.playerUuid),
            playerName = record.playerName,
            proxyId = UUID.fromString(record.proxyId),
            serverId = record.serverId.ifEmpty { null }?.let(UUID::fromString),
            permissionGroupId = record.permissionGroupId,
            rankVersion = record.rankVersion,
            connectedAt = record.connectedAt.toInstant(),
        )
    }

    private fun Instant.toTimestamp(): Timestamp =
        Timestamp
            .newBuilder()
            .setSeconds(epochSecond)
            .setNanos(nano)
            .build()

    private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())
}
