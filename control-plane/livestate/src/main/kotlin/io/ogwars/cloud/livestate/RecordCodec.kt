package io.ogwars.cloud.livestate

import io.ogwars.cloud.contracts.internal.v1.ServerRecord
import io.ogwars.cloud.contracts.toInstant
import io.ogwars.cloud.contracts.toTimestamp
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.session.PlayerSession
import java.util.UUID
import io.ogwars.cloud.contracts.internal.v1.PlayerSession as PlayerSessionRecord

/** Maps domain objects to the Protobuf records shared with the Go gateway. */
internal object RecordCodec {
    fun encode(server: Server): ByteArray =
        ServerRecord
            .newBuilder()
            .setServerId(server.id.toString())
            .setGroupId(server.groupId.value)
            .setGroupType(server.groupType.toProto())
            .setProfileId(server.profileId)
            .setState(server.state.toProto())
            .setStateSince(server.stateSince.toTimestamp())
            .setGameState(server.gameState.toProto())
            .setHost(server.host.orEmpty())
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
                id = record.serverId.storedUuid(),
                groupId = GroupId(record.groupId),
                groupType = record.groupType.toDomain(),
                profileId = record.profileId,
                state = record.state.toDomain(),
                stateSince = record.stateSince.toInstant(),
                players = record.players,
                maxPlayers = record.maxPlayers,
                gameState = record.gameState.toDomain(),
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
                playerUuid = record.playerUuid.storedUuid(),
                playerName = record.playerName,
                proxyId = record.proxyId.storedUuid(),
                serverId = record.serverId.ifEmpty { null }?.let(UUID::fromString),
                permissionGroupId = record.permissionGroupId,
                rankVersion = record.rankVersion,
                connectedAt = record.connectedAt.toInstant(),
            )
        }

    private inline fun <T> decoding(
        key: String,
        decode: () -> T,
    ): T =
        try {
            decode()
        } catch (e: Exception) {
            throw IllegalStateException("Unreadable record at $key", e)
        }
}
