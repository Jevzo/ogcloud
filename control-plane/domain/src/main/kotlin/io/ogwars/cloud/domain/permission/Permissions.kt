package io.ogwars.cloud.domain.permission

import io.ogwars.cloud.domain.text.requireText
import io.ogwars.cloud.text.TextField
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * How a rank appears in chat and the tablist, in the tag format (D-0200). The name format shows `{name}`; empty, the
 * catalog's default applies (D-0203).
 */
data class RankDisplay(
    val chatPrefix: String = "",
    val chatSuffix: String = "",
    val nameFormat: String = "",
    val tabPrefix: String = "",
) {
    init {
        requireText("chatPrefix", TextField.CHAT_PREFIX, chatPrefix)
        requireText("chatSuffix", TextField.CHAT_SUFFIX, chatSuffix)
        requireText("nameFormat", TextField.NAME_FORMAT, nameFormat)
        requireText("tabPrefix", TextField.TAB_PREFIX, tabPrefix)
    }
}

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
    init {
        require(
            ID.matches(id),
        ) { "Permission group id must be lowercase letters, digits, '_' or '-', starting with a letter" }
        require(name.isNotBlank()) { "Permission group name must not be blank" }
        require(permissions.keys.none(String::isBlank)) { "Permission nodes must not be blank" }
    }

    /** Exact permission nodes, or everything for `*` (v1 semantics). */
    fun grants(permission: String): Boolean = WILDCARD in permissions || permission in permissions

    private companion object {
        const val WILDCARD = "*"
        val ID = Regex("^[a-z][a-z0-9_-]{0,31}$")
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
        /** A null [duration] makes the rank permanent. */
        fun assign(
            playerUuid: UUID,
            groupId: String,
            duration: Duration?,
            now: Instant,
        ): PlayerRank {
            require(duration == null || duration.isPositive) { "A timed rank must last longer than zero" }
            val expiresAt =
                duration?.let {
                    runCatching { now.plus(it) }.getOrElse { e ->
                        throw IllegalArgumentException("Rank duration ends beyond the supported date range", e)
                    }
                }
            return PlayerRank(playerUuid = playerUuid, groupId = groupId, expiresAt = expiresAt)
        }
    }
}
