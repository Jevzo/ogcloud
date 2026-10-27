package io.ogwars.cloud.api.logs

import io.ogwars.cloud.api.web.Cursors
import io.ogwars.cloud.api.web.Page
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.scaling.ScalingAction
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.scaling.ScalingReason
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class AuditEntryView(
    val id: UUID,
    val occurredAt: Instant,
    /** e.g. `user:0192…`; pseudonymised after a privacy erasure (D-0049). */
    val actor: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val summary: String,
    val metadata: Map<String, String>,
)

data class ScalingLogView(
    val id: UUID,
    val occurredAt: Instant,
    val groupId: String,
    val action: ScalingAction,
    val serverId: UUID?,
    val reason: ScalingReason,
    val details: String,
)

fun ScalingLogEntry.toView() = ScalingLogView(id, occurredAt, groupId.value, action, serverId, reason, details)

private fun AuditEntry.toView() =
    AuditEntryView(id, occurredAt, actor.toString(), action, targetType, targetId, summary, metadata)

/** The audit and scaling logs, newest first (D-0104); retention trims them (D-0043). */
@RestController
@RequestMapping("/api/v1/logs")
class LogController(
    private val audit: AuditLogStore,
    private val scaling: ScalingLogStore,
) {
    @Requires(ApiPermission.LOGS_READ)
    @GetMapping("/audit")
    fun audit(
        @RequestParam cursor: String?,
        @RequestParam limit: Int?,
    ): Page<AuditEntryView> {
        val size = Cursors.limit(limit)
        val page = Cursors.page(audit.list(cursor?.let(Cursors::decode), size + 1), size) { it.id }
        return Page(page.items.map { it.toView() }, page.nextCursor)
    }

    @Requires(ApiPermission.LOGS_READ)
    @GetMapping("/scaling")
    fun scaling(
        @RequestParam groupId: String?,
        @RequestParam cursor: String?,
        @RequestParam limit: Int?,
    ): Page<ScalingLogView> {
        val size = Cursors.limit(limit)
        val page =
            Cursors.page(
                scaling.list(groupId?.let(::GroupId), cursor?.let(Cursors::decode), size + 1),
                size,
            ) { it.id }
        return Page(page.items.map { it.toView() }, page.nextCursor)
    }
}
