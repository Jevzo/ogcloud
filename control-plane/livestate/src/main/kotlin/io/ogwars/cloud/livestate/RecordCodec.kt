package io.ogwars.cloud.livestate

import io.ogwars.cloud.contracts.internal.v1.ServerRecord
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.GameState
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.session.PlayerSession
import com.google.protobuf.Timestamp
import java.time.Instant
import java.util.UUID
import io.ogwars.cloud.contracts.events.v1.GameState as ProtoGameState
import io.ogwars.cloud.contracts.events.v1.GroupType as ProtoGroupType
import io.ogwars.cloud.contracts.events.v1.ServerState as ProtoServerState
import io.ogwars.cloud.contracts.internal.v1.PlayerSession as PlayerSessionRecord

/** Maps domain objects to the Protobuf records shared with the Go gateway. */
internal object RecordCodec {
    fun encode(server: Server): ByteArray =
        ServerRecord
            .newBuilder()
            .setServerId(server.id.toString())
            .setGroupId(server.groupId.value)
            .setGroupType(ProtoGroupType.valueOf("GROUP_TYPE_${server.groupType.name}"))
            .setProfileId(server.profileId)
            .setState(ProtoServerState.valueOf("SERVER_STATE_${server.state.name}"))
            .setStateSince(server.stateSince.toTimestamp())
            .setGameState(
                server.gameState?.let { ProtoGameState.valueOf("GAME_STATE_${it.name}") }
                    ?: ProtoGameState.GAME_STATE_UNSPECIFIED,
            ).setHost(server.host.orEmpty())
            .setPort(server.port ?: 0)
            .setPlayers(server.players)
            .setMaxPlayers(server.maxPlayers)
            .apply { server.startedAt?.let { setStartedAt(it.toTimestamp()) } }
            .apply { server.lastHeartbeatAt?.let { setLastHeartbeatAt(it.toTimestamp()) } }
            .build()
            .toByteArray()

    fun decodeServer(
        key: String,
        bytes: ByteArray,
    ): Server =
        decoding(key) {
            val record = ServerRecord.parseFrom(bytes)
            Server(
                id = UUID.fromString(record.serverId),
                groupId = GroupId(record.groupId),
                groupType = enumFrom<GroupType>(record.groupType.name, "GROUP_TYPE_"),
                profileId = record.profileId,
                state = enumFrom<ServerState>(record.state.name, "SERVER_STATE_"),
                stateSince = record.stateSince.toInstant(),
                players = record.players,
                maxPlayers = record.maxPlayers,
                gameState =
                    record.gameState
                        .takeUnless { it == ProtoGameState.GAME_STATE_UNSPECIFIED }
                        ?.let { enumFrom<GameState>(it.name, "GAME_STATE_") },
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

    fun decodeSession(
        key: String,
        bytes: ByteArray,
    ): PlayerSession =
        decoding(key) {
            val record = PlayerSessionRecord.parseFrom(bytes)
            PlayerSession(
                playerUuid = UUID.fromString(record.playerUuid),
                playerName = record.playerName,
                proxyId = UUID.fromString(record.proxyId),
                serverId = record.serverId.ifEmpty { null }?.let(UUID::fromString),
                permissionGroupId = record.permissionGroupId,
                rankVersion = record.rankVersion,
                connectedAt = record.connectedAt.toInstant(),
            )
        }

    /** Maps a Protobuf enum name to the domain enum; unknown or unspecified values are errors. */
    private inline fun <reified E : Enum<E>> enumFrom(
        protoName: String,
        prefix: String,
    ): E =
        enumValues<E>().find { prefix + it.name == protoName }
            ?: throw IllegalStateException("No ${E::class.simpleName} for Protobuf value $protoName")

    private inline fun <T> decoding(
        key: String,
        decode: () -> T,
    ): T =
        try {
            decode()
        } catch (e: Exception) {
            throw IllegalStateException("Unreadable record at $key", e)
        }

    private fun Instant.toTimestamp(): Timestamp =
        Timestamp
            .newBuilder()
            .setSeconds(epochSecond)
            .setNanos(nano)
            .build()

    private fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())
}
