package io.ogwars.cloud.controller.repository

import io.ogwars.cloud.controller.model.PermissionGroupDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface PermissionGroupRepository : MongoRepository<PermissionGroupDocument, String> {

    fun findByDefaultTrue(): PermissionGroupDocument?
}
