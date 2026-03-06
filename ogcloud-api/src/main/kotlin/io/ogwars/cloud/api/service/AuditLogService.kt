package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.model.ApiAuditLogDocument
import io.ogwars.cloud.api.model.ScalingLogDocument
import io.ogwars.cloud.api.repository.ApiAuditLogRepository
import io.ogwars.cloud.api.security.AuthenticatedUser
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.count
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class AuditLogService(
    private val apiAuditLogRepository: ApiAuditLogRepository,
    private val mongoTemplate: MongoTemplate
) {

    fun logApiAction(
        action: String,
        targetType: String,
        targetId: String,
        summary: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        val actor = currentUser()
        apiAuditLogRepository.save(
            ApiAuditLogDocument(
                action = action,
                targetType = targetType,
                targetId = targetId,
                actorUserId = actor?.id,
                actorEmail = actor?.email,
                summary = summary,
                metadata = metadata
            )
        )
    }

    fun listApiLogs(query: String?, page: Int, size: Int?): PaginatedResponse<ApiAuditLogResponse> {
        val pageRequest = PaginationSupport.toPageRequest(page, size)
        val queryObject = Query()

        PaginationSupport.buildSearchCriteria(
            query,
            "action",
            "targetType",
            "targetId",
            "actorUserId",
            "actorEmail",
            "summary"
        )?.let(queryObject::addCriteria)

        val totalItems = mongoTemplate.count<ApiAuditLogDocument>(queryObject)

        queryObject.with(
            Sort.by(
                Sort.Order.desc("timestamp"),
                Sort.Order.desc("_id")
            )
        ).with(pageRequest)

        val logs = mongoTemplate.find<ApiAuditLogDocument>(queryObject)
            .map { it.toResponse() }

        return PaginationSupport.toResponse(logs, page, pageRequest.pageSize, totalItems)
    }

    fun listScalingLogs(query: String?, page: Int, size: Int?): PaginatedResponse<ScalingLogResponse> {
        val pageRequest = PaginationSupport.toPageRequest(page, size)
        val queryObject = Query()

        PaginationSupport.buildSearchCriteria(
            query,
            "groupId",
            "action",
            "reason",
            "serverId",
            "details"
        )?.let(queryObject::addCriteria)

        val totalItems = mongoTemplate.count<ScalingLogDocument>(queryObject)

        queryObject.with(
            Sort.by(
                Sort.Order.desc("timestamp"),
                Sort.Order.desc("_id")
            )
        ).with(pageRequest)

        val logs = mongoTemplate.find<ScalingLogDocument>(queryObject)
            .map { it.toResponse() }

        return PaginationSupport.toResponse(logs, page, pageRequest.pageSize, totalItems)
    }

    private fun currentUser(): AuthenticatedUser? {
        return SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
    }
}
