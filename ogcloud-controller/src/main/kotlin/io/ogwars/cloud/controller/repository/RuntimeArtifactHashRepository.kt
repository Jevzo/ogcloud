package io.ogwars.cloud.controller.repository

import io.ogwars.cloud.common.model.RuntimeBundleScope
import io.ogwars.cloud.controller.model.RuntimeArtifactHashDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface RuntimeArtifactHashRepository : MongoRepository<RuntimeArtifactHashDocument, String> {
    fun findByScope(scope: RuntimeBundleScope): List<RuntimeArtifactHashDocument>
}
