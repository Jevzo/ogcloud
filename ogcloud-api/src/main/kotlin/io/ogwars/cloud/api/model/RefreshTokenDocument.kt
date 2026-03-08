package io.ogwars.cloud.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "web_user_refresh_tokens")
data class RefreshTokenDocument(
    @Id val id: String,
    @field:Indexed val userId: String,
    @field:Indexed(unique = true) val tokenHash: String,
    @field:Indexed(expireAfter = "0s") val expiresAt: Instant,
    val createdAt: Instant,
)
