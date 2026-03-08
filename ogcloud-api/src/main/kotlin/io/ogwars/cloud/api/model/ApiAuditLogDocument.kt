package io.ogwars.cloud.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "api_audit_log")
data class ApiAuditLogDocument(
    @Id val id: String? = null,
    val action: String,
    val targetType: String,
    val targetId: String,
    val actorUserId: String? = null,
    val actorEmail: String? = null,
    val summary: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Instant = Instant.now(),
)
