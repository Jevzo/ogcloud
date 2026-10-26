package io.ogwars.cloud.domain.permission

import java.time.Instant
import java.util.UUID

/** How a rank appears in chat and the tablist; colours use `&` codes. */
data class RankDisplay(
    val chatPrefix: String = "",
    val chatSuffix: String = "",
    val nameColor: String = "",
    val tabPrefix: String = "",
)

data class PermissionGroup(
    val id: String,
    val name: String,
    /** Higher weights sort first in the tablist. */
    val weight: Int,
    /** Permission node to its human-readable description. */
    val permissions: Map<String, String>,
    val display: RankDisplay,
    val isDefault: Boolean,
) {
    /** Exact permission nodes, or everything for `*` (v1 semantics). */
    fun grants(permission: String): Boolean = WILDCARD in permissions || permission in permissions

    private companion object {
        const val WILDCARD = "*"
    }
}

/** A player's rank; [expiresAt] is null for permanent ranks. */
data class PlayerRank(
    val playerUuid: UUID,
    val groupId: String,
    val expiresAt: Instant?,
) {
    fun isExpired(now: Instant): Boolean = expiresAt != null && !now.isBefore(expiresAt)

    /** The group that applies now: an expired timed rank falls back to the default group. */
    fun effectiveGroupId(
        now: Instant,
        defaultGroupId: String,
    ): String = if (isExpired(now)) defaultGroupId else groupId

    companion object {
        fun assign(
            playerUuid: UUID,
            groupId: String,
            duration: RankDuration,
            now: Instant,
        ): PlayerRank =
            PlayerRank(
                playerUuid = playerUuid,
                groupId = groupId,
                expiresAt =
                    when (duration) {
                        RankDuration.Permanent -> {
                            null
                        }

                        is RankDuration.Timed -> {
                            runCatching { now.plus(duration.duration) }
                                .getOrElse {
                                    throw IllegalArgumentException(
                                        "Rank duration ends beyond the supported date range",
                                        it,
                                    )
                                }
                        }
                    },
            )
    }
}
