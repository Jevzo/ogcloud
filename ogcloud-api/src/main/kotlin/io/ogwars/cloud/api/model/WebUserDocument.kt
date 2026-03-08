package io.ogwars.cloud.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "web_users")
data class WebUserDocument(
    @Id val id: String,
    @field:Indexed(unique = true) val email: String,
    val username: String,
    @field:Field("password") val passwordHash: String,
    val role: WebUserRole,
    @field:Indexed(unique = true, sparse = true) val linkedPlayerUuid: String? = null,
)
