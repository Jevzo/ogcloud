package io.ogwars.cloud.domain.session

import java.time.Instant
import java.util.UUID

/** An online player: which proxy holds them, which server they are on, which rank applies. */
data class PlayerSession(
    val playerUuid: UUID,
    val playerName: String,
    val proxyId: UUID,
    /** Null until the player reaches a backend. */
    val serverId: UUID?,
    val permissionGroupId: String,
    val rankVersion: Long,
    val connectedAt: Instant,
)
