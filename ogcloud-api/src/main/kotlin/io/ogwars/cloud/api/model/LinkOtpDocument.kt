package io.ogwars.cloud.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "web_user_link_otps")
data class LinkOtpDocument(
    @Id val id: String,
    @field:Indexed val userId: String,
    val playerUuid: String,
    val otpHash: String,
    @field:Indexed(expireAfter = "0s") val expiresAt: Instant,
    val createdAt: Instant,
)
