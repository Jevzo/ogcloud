package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.player.Player
import io.ogwars.cloud.domain.store.PlayerStore
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JdbcPlayerStore(
    private val jdbc: JdbcClient,
) : PlayerStore {
    override fun find(uuid: UUID): Player? =
        jdbc
            .sql("select * from players where uuid = :uuid")
            .param("uuid", uuid)
            .query(PLAYER)
            .optional()
            .orElse(null)

    override fun names(uuids: Collection<UUID>): Map<UUID, String> =
        if (uuids.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .sql("select uuid, name from players where uuid in (:uuids)")
                .param("uuids", uuids)
                .query { rs, _ -> rs.uuid("uuid") to rs.getString("name") }
                .list()
                .toMap()
        }

    // Names change; the most recently seen holder of a name wins.
    override fun findByName(name: String): Player? =
        jdbc
            .sql("select * from players where lower(name) = lower(:name) order by last_seen_at desc limit 1")
            .param("name", name)
            .query(PLAYER)
            .optional()
            .orElse(null)

    override fun list(
        namePrefix: String?,
        after: UUID?,
        limit: Int,
    ): List<Player> =
        jdbc
            .sql(
                "select * from players where lower(name) like :pattern escape '\\' and uuid > :after order by uuid limit :limit",
            ).param("pattern", namePrefix?.lowercase()?.let { likePrefix(it) } ?: "%")
            .param("after", after ?: MIN_ID)
            .param("limit", limit)
            .query(PLAYER)
            .list()

    override fun recordSeen(
        uuid: UUID,
        name: String,
        at: Instant,
        defaultRank: PlayerRank,
    ): Player =
        jdbc
            .sql(
                """
                insert into players (uuid, name, permission_group_id, rank_expires_at, first_seen_at, last_seen_at)
                values (:uuid, :name, :group, :expiresAt, :at, :at)
                on conflict (uuid) do update set name = excluded.name, last_seen_at = excluded.last_seen_at
                returning *
                """.trimIndent(),
            ).param("uuid", uuid)
            .param("name", name)
            .param("group", defaultRank.groupId)
            .param("expiresAt", defaultRank.expiresAt?.toTimestamp())
            .param("at", at.toTimestamp())
            .query(PLAYER)
            .single()

    override fun updateRank(rank: PlayerRank): Long =
        jdbc
            .sql(
                """
                update players set permission_group_id = :group, rank_expires_at = :expiresAt,
                    rank_version = rank_version + 1
                where uuid = :uuid
                returning rank_version
                """.trimIndent(),
            ).param("uuid", rank.playerUuid)
            .param("group", rank.groupId)
            .param("expiresAt", rank.expiresAt?.toTimestamp())
            .query(Long::class.java)
            .optional()
            .orElseThrow { NoSuchElementException("Player ${rank.playerUuid} does not exist") }

    override fun resetExpiredRank(
        playerUuid: UUID,
        defaultGroupId: String,
        now: Instant,
    ): Long? =
        jdbc
            .sql(
                """
                update players set permission_group_id = :group, rank_expires_at = null, rank_version = rank_version + 1
                where uuid = :uuid and rank_expires_at <= :now
                returning rank_version
                """.trimIndent(),
            ).param("uuid", playerUuid)
            .param("group", defaultGroupId)
            .param("now", now.toTimestamp())
            .query(Long::class.java)
            .optional()
            .orElse(null)

    override fun withExpiredRanks(
        now: Instant,
        limit: Int,
    ): List<Player> =
        jdbc
            .sql("select * from players where rank_expires_at <= :now order by rank_expires_at limit :limit")
            .param("now", now.toTimestamp())
            .param("limit", limit)
            .query(PLAYER)
            .list()

    private companion object {
        /** `abc%`, with the caller's own wildcards taken literally. */
        fun likePrefix(prefix: String) = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

        val PLAYER =
            RowMapper { rs, _ ->
                val uuid = rs.uuid("uuid")
                Player(
                    uuid = uuid,
                    name = rs.getString("name"),
                    rank = PlayerRank(uuid, rs.getString("permission_group_id"), rs.instantOrNull("rank_expires_at")),
                    rankVersion = rs.getLong("rank_version"),
                    firstSeenAt = rs.instant("first_seen_at"),
                    lastSeenAt = rs.instant("last_seen_at"),
                )
            }
    }
}
