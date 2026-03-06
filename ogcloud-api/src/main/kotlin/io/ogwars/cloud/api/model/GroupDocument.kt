package io.ogwars.cloud.api.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "groups")
data class GroupDocument(
    @Id val id: String,
    val type: GroupType,
    val templateBucket: String,
    val templatePath: String,
    val templateVersion: String,
    val scaling: ScalingConfig,
    val resources: ResourceConfig,
    val jvmFlags: String,
    val drainTimeoutSeconds: Int,
    val serverImage: String,
    val storageSize: String = "5Gi",
    val maintenance: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
