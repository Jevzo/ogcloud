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
        records.findByGroupIdOrderByCreatedAtDesc(groupId.value).map(::toDomain)

    override fun find(id: UUID): TemplateVersion? = records.findById(id).map(::toDomain).orElse(null)

    private fun toDomain(record: TemplateVersionRecord) =
        TemplateVersion(
            id = record.id,
            groupId = GroupId(record.groupId),
            objectKey = record.objectKey,
            sha256 = record.sha256,
            sizeBytes = record.sizeBytes,
            source = TemplateSource.valueOf(record.source),
            createdBy = Actor.parse(record.createdBy),
            createdAt = record.createdAt,
        )

    override fun delete(id: UUID) = records.deleteById(id)
}
