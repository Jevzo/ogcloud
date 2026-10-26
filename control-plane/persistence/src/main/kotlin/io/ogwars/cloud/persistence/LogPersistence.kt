package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.retention.RetentionKind
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.RetentionStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

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
}

@Repository
class JdbcRetentionStore(
    private val jdbc: JdbcClient,
) : RetentionStore {
    // Deleting through a limited sub-select keeps each batch, and its locks, short.
    override fun deleteOlderThan(
        kind: RetentionKind,
        cutoff: Instant,
        limit: Int,
    ): Int {
        val (table, column) = targets[kind] ?: throw UnsupportedOperationException("No table holds $kind yet")
        return jdbc
            .sql("delete from $table where id in (select id from $table where $column < :cutoff limit :limit)")
            .param("cutoff", cutoff.toTimestamp())
            .param("limit", limit)
            .update()
    }

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
