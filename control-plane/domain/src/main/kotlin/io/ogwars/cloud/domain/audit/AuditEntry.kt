package io.ogwars.cloud.domain.audit

import java.time.Instant
import java.util.UUID

data class AuditEntry(
    val id: UUID,
    val occurredAt: Instant,
    val actor: Actor,
    val action: String,
    val targetType: String,
    val targetId: String,
    val summary: String = "",
    val metadata: Map<String, String> = emptyMap(),
)
