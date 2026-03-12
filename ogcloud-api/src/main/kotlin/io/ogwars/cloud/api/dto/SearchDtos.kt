package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.GroupDocument
import io.ogwars.cloud.api.model.PlayerDocument
import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.RedisPlayerSession
import io.ogwars.cloud.common.model.ServerState

data class SearchGroupResult(
    val id: String,
    val type: GroupType,
    val maintenance: Boolean,
)

data class SearchServerResult(
    val id: String,
    val group: String,
    val type: GroupType,
    val displayName: String,
    val podName: String,
    val state: ServerState,
)

data class SearchPlayerResult(
    val uuid: String,
    val name: String,
    val permissionGroup: String,
    val firstJoin: String,
    val online: Boolean,
    val proxyId: String?,
    val serverId: String?,
    val connectedAt: String?,
)

data class SearchResponse(
    val query: String,
    val limit: Int,
    val groups: List<SearchGroupResult>,
    val servers: List<SearchServerResult>,
    val players: List<SearchPlayerResult>,
)

fun GroupDocument.toSearchResult(): SearchGroupResult =
    SearchGroupResult(
        id = id,
        type = type,
        maintenance = maintenance,
    )

fun ServerDocument.toSearchResult(): SearchServerResult =
    SearchServerResult(
        id = id,
        group = group,
        type = type,
        displayName = displayName,
        podName = podName,
        state = state,
    )

fun PlayerDocument.toSearchResult(session: RedisPlayerSession?): SearchPlayerResult =
    SearchPlayerResult(
        uuid = id,
        name = name,
        permissionGroup = permission.group,
        firstJoin = firstJoin.toString(),
        online = session != null,
        proxyId = session?.proxyId,
        serverId = session?.serverId,
        connectedAt = session?.connectedAt?.toString(),
    )
