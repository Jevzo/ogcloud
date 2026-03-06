package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.LinkOtpDocument
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.Optional

interface LinkOtpRepository : MongoRepository<LinkOtpDocument, String> {

    fun findByUserId(userId: String): Optional<LinkOtpDocument>
}
