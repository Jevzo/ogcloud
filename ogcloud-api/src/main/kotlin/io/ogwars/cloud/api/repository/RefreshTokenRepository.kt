package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.RefreshTokenDocument
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.Optional

interface RefreshTokenRepository : MongoRepository<RefreshTokenDocument, String> {

    fun findByTokenHash(tokenHash: String): Optional<RefreshTokenDocument>

    fun findAllByUserId(userId: String): List<RefreshTokenDocument>
}
