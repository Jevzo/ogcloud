package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.runtime.RuntimeArtifact
import io.ogwars.cloud.domain.store.RuntimeArtifactStore
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcRuntimeArtifactStore(
    private val jdbc: JdbcClient,
) : RuntimeArtifactStore {
    override fun findByProfile(profileId: String): List<RuntimeArtifact> =
        jdbc
            .sql("select * from runtime_artifacts where profile_id = :profile order by path")
            .param("profile", profileId)
            .query(ARTIFACT)
            .list()

    override fun save(artifact: RuntimeArtifact) {
        jdbc
            .sql(
                """
                insert into runtime_artifacts (profile_id, path, object_key, source_url, sha256, size_bytes, stored_at)
                values (:profile, :path, :key, :url, :sha, :size, :at)
                on conflict (profile_id, path) do update set object_key = excluded.object_key,
                    source_url = excluded.source_url, sha256 = excluded.sha256, size_bytes = excluded.size_bytes,
                    stored_at = excluded.stored_at
                """.trimIndent(),
            ).param("profile", artifact.profileId)
            .param("path", artifact.path)
            .param("key", artifact.objectKey)
            .param("url", artifact.sourceUrl)
            .param("sha", artifact.sha256)
            .param("size", artifact.sizeBytes)
            .param("at", artifact.storedAt.toTimestamp())
            .update()
    }

    override fun delete(
        profileId: String,
        path: String,
    ) {
        jdbc
            .sql("delete from runtime_artifacts where profile_id = :profile and path = :path")
            .param("profile", profileId)
            .param("path", path)
            .update()
    }

    private companion object {
        val ARTIFACT =
            RowMapper { rs, _ ->
                RuntimeArtifact(
                    profileId = rs.getString("profile_id"),
                    path = rs.getString("path"),
                    objectKey = rs.getString("object_key"),
                    sourceUrl = rs.getString("source_url"),
                    sha256 = rs.getString("sha256"),
                    sizeBytes = rs.getLong("size_bytes"),
                    storedAt = rs.instant("stored_at"),
                )
            }
    }
}
