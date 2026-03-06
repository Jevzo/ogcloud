package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.PlayerDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface PlayerRepository : MongoRepository<PlayerDocument, String> {

    fun findByPermission_Group(group: String): List<PlayerDocument>

    fun findByNameIgnoreCase(name: String): PlayerDocument?
}
