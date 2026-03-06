package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.ApiAuditLogDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface ApiAuditLogRepository : MongoRepository<ApiAuditLogDocument, String>
