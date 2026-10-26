package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Table("template_versions")
data class TemplateVersionRecord(
    @Id val id: UUID,
    val groupId: String,
    val objectKey: String,
    val sha256: String,
    val sizeBytes: Long,
    val source: String,
    val createdBy: String,
    val createdAt: Instant,
)

interface TemplateVersionRecordRepository : CrudRepository<TemplateVersionRecord, UUID> {
    fun findByGroupIdOrderByCreatedAtDesc(groupId: String): List<TemplateVersionRecord>
}

@Repository
class JdbcTemplateVersionStore(
    private val records: TemplateVersionRecordRepository,
    private val aggregates: JdbcAggregateTemplate,
) : TemplateVersionStore {
    // Versions are immutable and carry their own id, so they are always inserted.
    override fun add(version: TemplateVersion) {
        aggregates.insert(
            TemplateVersionRecord(
                id = version.id,
                groupId = version.groupId.value,
                objectKey = version.objectKey,
                sha256 = version.sha256,
                sizeBytes = version.sizeBytes,
                source = version.source.name,
                createdBy = version.createdBy.toString(),
                createdAt = version.createdAt,
            ),
        )
    }

    override fun findByGroup(groupId: GroupId): List<TemplateVersion> =
        records.findByGroupIdOrderByCreatedAtDesc(groupId.value).map {
            TemplateVersion(
                id = it.id,
                groupId = GroupId(it.groupId),
                objectKey = it.objectKey,
                sha256 = it.sha256,
                sizeBytes = it.sizeBytes,
                source = TemplateSource.valueOf(it.source),
                createdBy = Actor.parse(it.createdBy),
                createdAt = it.createdAt,
            )
        }

    override fun delete(id: UUID) = records.deleteById(id)
}
