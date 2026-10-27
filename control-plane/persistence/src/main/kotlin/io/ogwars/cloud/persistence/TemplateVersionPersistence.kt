package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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
    private val jdbc: JdbcClient,
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

    // Locks the group row, which a promote updates, so a version is never deleted while it becomes active.
    @Transactional
    override fun delete(id: UUID) {
        val active =
            jdbc
                .sql(
                    """
                    select g.active_template_version_id = v.id from template_versions v
                    join groups g on g.id = v.group_id
                    where v.id = :id
                    for update of g
                    """.trimIndent(),
                ).param("id", id)
                .query(Boolean::class.java)
                .optional()
        if (active.orElse(false)) throw ConflictException("Promote another version before deleting the active one")
        records.deleteById(id)
    }
}
