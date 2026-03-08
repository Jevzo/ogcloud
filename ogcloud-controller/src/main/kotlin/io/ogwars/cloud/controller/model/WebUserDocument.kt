package io.ogwars.cloud.controller.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "web_users")
data class WebUserDocument(
    @Id val id: String,
    val email: String,
    val username: String,
    val password: String,
    val role: String,
    val linkedPlayerUuid: String? = null,
)
