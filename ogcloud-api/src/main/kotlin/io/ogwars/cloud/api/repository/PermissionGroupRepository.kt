package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.PermissionGroupDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface PermissionGroupRepository : MongoRepository<PermissionGroupDocument, String> {
    fun findByDefaultTrue(): PermissionGroupDocument?
}
