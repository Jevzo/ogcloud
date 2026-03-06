package io.ogwars.cloud.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "scaling_log")
data class ScalingLogDocument(
    @Id val id: String? = null,
    val groupId: String,
    val action: String,
    val reason: String,
    val serverId: String? = null,
    val details: String? = null,
    val timestamp: Instant = Instant.now()
)
