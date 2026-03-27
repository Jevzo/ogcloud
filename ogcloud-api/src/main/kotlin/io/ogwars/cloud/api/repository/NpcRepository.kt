package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.NpcDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface NpcRepository : MongoRepository<NpcDocument, String> {
    fun findAllByGroup(group: String): List<NpcDocument>
}
