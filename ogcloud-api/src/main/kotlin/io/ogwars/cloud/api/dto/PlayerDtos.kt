package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.PermissionConfig
import io.ogwars.cloud.api.model.PlayerDocument
import io.ogwars.cloud.api.model.RedisPlayerSession
import jakarta.validation.constraints.NotBlank

data class PermissionConfigResponse(
    val group: String,
    val length: Long,
    val endMillis: Long
)

data class PlayerResponse(
    val uuid: String,
    val name: String,
    val permission: PermissionConfigResponse,
    val firstJoin: String,
    val online: Boolean,
    val proxyId: String?,
    val proxyDisplayName: String?,
    val serverId: String?,
    val serverDisplayName: String?,
    val connectedAt: String?
)

data class OnlinePlayerResponse(
    val uuid: String,
    val name: String,
    val proxyId: String?,
    val proxyDisplayName: String?,
    val serverId: String?,
    val serverDisplayName: String?,
    val groupId: String?,
    val connectedAt: String?
)

data class PersistedPlayerResponse(
    val uuid: String,
    val name: String,
    val permission: PermissionConfigResponse,
    val firstJoin: String,
    val online: Boolean,
    val proxyId: String?,
    val serverId: String?,
    val connectedAt: String?
)

data class SetPlayerGroupRequest(
    @field:NotBlank val group: String,
    @field:NotBlank val duration: String
)

data class TransferPlayerRequest(
    @field:NotBlank val target: String
)

fun PlayerDocument.toPlayerResponse(
    session: RedisPlayerSession?,
    serverDisplayName: String? = null,
    proxyDisplayName: String? = null
): PlayerResponse {
    return PlayerResponse(
        uuid = id,
        name = name,
        permission = permission.toResponse(),
        firstJoin = firstJoin.toString(),
        online = session != null,
        proxyId = session?.proxyId,
        proxyDisplayName = proxyDisplayName,
        serverId = session?.serverId,
        serverDisplayName = serverDisplayName,
        connectedAt = session?.connectedAt?.toString()
    )
}

fun RedisPlayerSession.toOnlinePlayerResponse(
    uuid: String,
    serverDisplayName: String? = null,
    proxyDisplayName: String? = null
): OnlinePlayerResponse {
    return OnlinePlayerResponse(
        uuid = uuid,
        name = name,
        proxyId = proxyId,
        proxyDisplayName = proxyDisplayName,
        serverId = serverId,
        serverDisplayName = serverDisplayName,
        groupId = permission.group,
        connectedAt = connectedAt.toString()
    )
}

fun PlayerDocument.toPersistedResponse(session: RedisPlayerSession?): PersistedPlayerResponse {
    return PersistedPlayerResponse(
        uuid = id,
        name = name,
        permission = permission.toResponse(),
        firstJoin = firstJoin.toString(),
        online = session != null,
        proxyId = session?.proxyId,
        serverId = session?.serverId,
        connectedAt = session?.connectedAt?.toString()
    )
}

private fun PermissionConfig.toResponse(): PermissionConfigResponse {
    return PermissionConfigResponse(
        group = group,
        length = length,
        endMillis = endMillis
    )
}
