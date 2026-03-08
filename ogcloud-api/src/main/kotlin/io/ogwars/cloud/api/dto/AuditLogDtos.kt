package io.ogwars.cloud.api.dto

import io.ogwars.cloud.api.model.ApiAuditLogDocument
import io.ogwars.cloud.api.model.ScalingLogDocument
import java.time.Instant

data class ApiAuditLogResponse(
    val id: String?,
    val action: String,
    val targetType: String,
    val targetId: String,
    val actorUserId: String?,
    val actorEmail: String?,
    val summary: String,
    val metadata: Map<String, String>,
    val timestamp: Instant,
)

data class ScalingLogResponse(
    val id: String?,
    val groupId: String,
    val action: String,
    val reason: String,
    val serverId: String?,
    val details: String?,
    val timestamp: Instant,
)

fun ApiAuditLogDocument.toResponse(): ApiAuditLogResponse =
    ApiAuditLogResponse(
        id = id,
        action = action,
        targetType = targetType,
        targetId = targetId,
        actorUserId = actorUserId,
        actorEmail = actorEmail,
        summary = summary,
        metadata = metadata,
        timestamp = timestamp,
    )

fun ScalingLogDocument.toResponse(): ScalingLogResponse =
    ScalingLogResponse(
        id = id,
        groupId = groupId,
        action = action,
        reason = reason,
        serverId = serverId,
        details = details,
        timestamp = timestamp,
    )
