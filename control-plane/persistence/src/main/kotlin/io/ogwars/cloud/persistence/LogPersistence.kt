package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.retention.RetentionKind
import io.ogwars.cloud.domain.scaling.ScalingAction
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.scaling.ScalingReason
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.RetentionStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JdbcAuditLogStore(
    private val jdbc: JdbcClient,
    private val json: JsonCodec,
) : AuditLogStore {
    override fun append(entry: AuditEntry) {
        jdbc
            .sql(
                """
                insert into audit_log (id, occurred_at, actor_type, actor_id, action, target_type, target_id, summary, metadata)
                values (:id, :at, :actorType, :actorId, :action, :targetType, :targetId, :summary, cast(:metadata as jsonb))
                """.trimIndent(),
            ).param("id", entry.id)
            .param("at", entry.occurredAt.toTimestamp())
            .param("actorType", entry.actor.type.name)
            .param("actorId", entry.actor.id)
            .param("action", entry.action)
            .param("targetType", entry.targetType)
            .param("targetId", entry.targetId)
            .param("summary", entry.summary)
            .param("metadata", json.write(entry.metadata))
            .update()
    }

    override fun list(
        before: UUID?,
        limit: Int,
    ): List<AuditEntry> =
        jdbc
            .sql("select * from audit_log where id < :before order by id desc limit :limit")
            .param("before", before ?: MAX_ID)
            .param("limit", limit)
            .query { rs, _ ->
                AuditEntry(
                    id = rs.uuid("id"),
                    occurredAt = rs.instant("occurred_at"),
                    actor = Actor(ActorType.valueOf(rs.getString("actor_type")), rs.getString("actor_id")),
                    action = rs.getString("action"),
                    targetType = rs.getString("target_type"),
                    targetId = rs.getString("target_id"),
                    summary = rs.getString("summary"),
                    metadata = json.readStringMap(rs.getString("metadata")),
                )
            }.list()
}

@Repository
class JdbcScalingLogStore(
    private val jdbc: JdbcClient,
) : ScalingLogStore {
    override fun append(entry: ScalingLogEntry) {
        jdbc
            .sql(
                """
                insert into scaling_log (id, occurred_at, group_id, action, server_id, reason, details)
                values (:id, :at, :group, :action, :server, :reason, :details)
                """.trimIndent(),
            ).param("id", entry.id)
            .param("at", entry.occurredAt.toTimestamp())
            .param("group", entry.groupId.value)
            .param("action", entry.action.name)
            .param("server", entry.serverId)
            .param("reason", entry.reason.name)
            .param("details", entry.details)
            .update()
    }

    override fun list(
        groupId: GroupId?,
        before: UUID?,
        limit: Int,
    ): List<ScalingLogEntry> =
        jdbc
            .sql(
                """
                select * from scaling_log
                where id < :before and (cast(:group as text) is null or group_id = :group)
                order by id desc limit :limit
                """.trimIndent(),
            ).param("before", before ?: MAX_ID)
            .param("group", groupId?.value)
            .param("limit", limit)
            .query { rs, _ ->
                ScalingLogEntry(
                    id = rs.uuid("id"),
                    occurredAt = rs.instant("occurred_at"),
                    groupId = GroupId(rs.getString("group_id")),
                    action = ScalingAction.valueOf(rs.getString("action")),
                    serverId = rs.uuidOrNull("server_id"),
                    reason = ScalingReason.valueOf(rs.getString("reason")),
                    details = rs.getString("details"),
                )
            }.list()
}

@Repository
class JdbcRetentionStore(
    private val jdbc: JdbcClient,
) : RetentionStore {
    override val kinds: Set<RetentionKind> get() = targets.keys

    override fun countOlderThan(
        kind: RetentionKind,
        cutoff: Instant,
    ): Long {
        val (table, column) = target(kind)
        return jdbc
            .sql("select count(*) from $table where $column < :cutoff")
            .param("cutoff", cutoff.toTimestamp())
            .query(Long::class.java)
            .single()
    }

    // Deleting through a limited sub-select keeps each batch, and its locks, short.
    override fun deleteOlderThan(
        kind: RetentionKind,
        cutoff: Instant,
        limit: Int,
    ): Int {
        val (table, column) = target(kind)
        return jdbc
            .sql("delete from $table where id in (select id from $table where $column < :cutoff limit :limit)")
            .param("cutoff", cutoff.toTimestamp())
            .param("limit", limit)
            .update()
    }

    private fun target(kind: RetentionKind) =
        targets[kind] ?: throw UnsupportedOperationException("No table holds $kind yet")

    private companion object {
        // Credentials (tokens, OTPs, push registrations) join with their own tables in later migrations.
        val targets: Map<RetentionKind, Pair<String, String>> =
            mapOf(
                RetentionKind.AUDIT_LOG to ("audit_log" to "occurred_at"),
                RetentionKind.SCALING_LOG to ("scaling_log" to "occurred_at"),
                RetentionKind.FINISHED_JOB to ("jobs" to "finished_at"),
            )
    }
}
