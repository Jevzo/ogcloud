package io.ogwars.cloud.domain.player

import io.ogwars.cloud.domain.permission.PlayerRank
import java.time.Instant
import java.util.UUID

data class Player(
    val uuid: UUID,
    val name: String,
    val rank: PlayerRank,
    /** Bumped on every rank change so nodes can drop stale updates. */
    val rankVersion: Long,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)
