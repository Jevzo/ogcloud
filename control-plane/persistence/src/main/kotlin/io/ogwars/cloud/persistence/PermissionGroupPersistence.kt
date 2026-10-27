package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.RankDisplay
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.Revisioned
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Table("permission_groups")
data class PermissionGroupRecord(
    @Id val id: String,
    val name: String,
    val weight: Int,
    val isDefault: Boolean,
    val chatPrefix: String,
    val chatSuffix: String,
    val nameColor: String,
    val tabPrefix: String,
    @MappedCollection(idColumn = "permission_group_id")
    val permissions: Set<PermissionRecord>,
    val createdAt: Instant,
    val updatedAt: Instant,
    @Version val version: Long,
)

@Table("permission_group_permissions")
data class PermissionRecord(
    val permission: String,
    val description: String,
)

interface PermissionGroupRecordRepository : CrudRepository<PermissionGroupRecord, String> {
    @Modifying
    @Query("update permission_groups set is_default = false, version = version + 1 where is_default and id <> :keep")
    fun clearDefaultExcept(keep: String): Int
}

@Repository
class JdbcPermissionGroupStore(
    private val records: PermissionGroupRecordRepository,
    private val aggregates: JdbcAggregateTemplate,
    private val clock: Clock,
) : PermissionGroupStore {
    override fun findAll(): List<Revisioned<PermissionGroup>> =
        records.findAll().map(::revisioned).sortedByDescending { it.value.weight }

    override fun find(id: String): Revisioned<PermissionGroup>? = records.findById(id).map(::revisioned).orElse(null)

    // The partial unique index guarantees one row; the migration seeds it.
    override fun findDefault(): Revisioned<PermissionGroup> =
        findAll().singleOrNull { it.value.isDefault } ?: throw IllegalStateException("No default permission group")

    private fun revisioned(record: PermissionGroupRecord) = Revisioned(toDomain(record), record.version)

    @Transactional
    override fun save(group: PermissionGroup) {
        if (group.isDefault) records.clearDefaultExcept(group.id)
        val existing = records.findById(group.id).orElse(null)
        val now = clock.instant()
        val record =
            PermissionGroupRecord(
                id = group.id,
                name = group.name,
                weight = group.weight,
                isDefault = group.isDefault,
                chatPrefix = group.display.chatPrefix,
                chatSuffix = group.display.chatSuffix,
                nameColor = group.display.nameColor,
                tabPrefix = group.display.tabPrefix,
                permissions =
                    group.permissions
                        .map { (node, description) ->
                            PermissionRecord(node, description)
                        }.toSet(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                version = existing?.version ?: 0,
            )
        // Explicit insert or update: the seeded rows start at version 0, which Spring Data would read as "new".
        if (existing == null) aggregates.insert(record) else aggregates.update(record)
    }

    override fun delete(id: String) = records.deleteById(id)

    private fun toDomain(record: PermissionGroupRecord) =
        PermissionGroup(
            id = record.id,
            name = record.name,
            weight = record.weight,
            permissions = record.permissions.associate { it.permission to it.description },
            display = RankDisplay(record.chatPrefix, record.chatSuffix, record.nameColor, record.tabPrefix),
            isDefault = record.isDefault,
        )
}
