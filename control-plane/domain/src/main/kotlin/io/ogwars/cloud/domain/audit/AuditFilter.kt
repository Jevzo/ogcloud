package io.ogwars.cloud.domain.audit

import java.time.Instant

/** Narrows the audit log (D-0213): every field that is set must match. */
data class AuditFilter(
    val actor: Actor? = null,
    /** The exact action, such as `POST /api/v1/npcs`. */
    val action: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val from: Instant? = null,
    /** Exclusive. */
    val until: Instant? = null,
) {
    init {
        if (from != null && until != null) require(from < until) { "The time range ends before it starts" }
    }

    companion object {
        val NONE = AuditFilter()
    }
}
