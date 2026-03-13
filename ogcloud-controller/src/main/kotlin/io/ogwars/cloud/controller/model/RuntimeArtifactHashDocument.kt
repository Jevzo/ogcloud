package io.ogwars.cloud.controller.model

import io.ogwars.cloud.common.model.RuntimeBundleScope
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "runtime_artifact_hashes")
data class RuntimeArtifactHashDocument(
    @Id val id: String,
    val scope: RuntimeBundleScope,
    val objectKey: String,
    val sourceUrl: String,
    val upstreamVersion: String,
    val upstreamBuild: Int,
    val sha256: String,
    val sizeBytes: Long,
    val updatedAt: Instant = Instant.now(),
)
