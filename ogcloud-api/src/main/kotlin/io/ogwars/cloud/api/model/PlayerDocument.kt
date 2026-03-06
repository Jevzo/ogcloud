package io.ogwars.cloud.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "players")
data class PlayerDocument(
    @Id val id: String,
    val name: String,
    val permission: PermissionConfig = PermissionConfig(),
    val firstJoin: Instant
)
