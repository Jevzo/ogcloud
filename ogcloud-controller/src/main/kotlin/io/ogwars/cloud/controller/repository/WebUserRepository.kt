package io.ogwars.cloud.controller.repository

import io.ogwars.cloud.controller.model.WebUserDocument
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.Optional

interface WebUserRepository : MongoRepository<WebUserDocument, String> {
    fun findByEmail(email: String): Optional<WebUserDocument>

    fun findByLinkedPlayerUuid(linkedPlayerUuid: String): Optional<WebUserDocument>
}
