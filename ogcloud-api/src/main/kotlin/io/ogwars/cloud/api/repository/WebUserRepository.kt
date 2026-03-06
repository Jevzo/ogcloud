package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.WebUserDocument
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.Optional

interface WebUserRepository : MongoRepository<WebUserDocument, String> {

    fun findByEmail(email: String): Optional<WebUserDocument>

    fun existsByEmail(email: String): Boolean

    fun findByLinkedPlayerUuid(linkedPlayerUuid: String): Optional<WebUserDocument>
}
