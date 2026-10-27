package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.RankDisplay
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.StaleUpdateException
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

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
    private val jdbc: JdbcClient,
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
    override fun create(group: PermissionGroup) {
        if (group.isDefault) records.clearDefaultExcept(group.id)
        val now = clock.instant()
        try {
            aggregates.insert(toRecord(group, createdAt = now, updatedAt = now, version = 0))
        } catch (e: DuplicateKeyException) {
            throw ConflictException("Permission group ${group.id} already exists").apply { initCause(e) }
        }
    }

    @Transactional
    override fun update(
        expectedVersion: Long,
        group: PermissionGroup,
    ) {
        val existing =
            records.findById(group.id).orElseThrow {
                NoSuchElementException("Permission group ${group.id} does not exist")
            }
        if (existing.isDefault && !group.isDefault) throw ConflictException("Make another group the default instead")
        if (group.isDefault) records.clearDefaultExcept(group.id)
        try {
            aggregates.update(
                toRecord(group, createdAt = existing.createdAt, updatedAt = clock.instant(), version = expectedVersion),
            )
        } catch (e: OptimisticLockingFailureException) {
            throw StaleUpdateException(
                "Permission group ${group.id} changed since version $expectedVersion",
            ).apply { initCause(e) }
        }
    }

    @Transactional
    override fun delete(id: String): List<UUID> {
        // Locked, so a concurrent update cannot make it the default while it goes.
        val isDefault =
            jdbc
                .sql("select is_default from permission_groups where id = :id for update")
                .param("id", id)
                .query(Boolean::class.java)
                .optional()
                .orElseThrow { NoSuchElementException("Permission group $id does not exist") }
        if (isDefault) throw ConflictException("The default permission group cannot be deleted")
        val moved =
            jdbc
                .sql(
                    """
                    update players set permission_group_id = :fallback, rank_expires_at = null, rank_version = rank_version + 1
                    where permission_group_id = :id
                    returning uuid
                    """.trimIndent(),
                ).param("fallback", findDefault().value.id)
                .param("id", id)
                .query { rs, _ -> rs.uuid("uuid") }
                .list()
        records.deleteById(id)
        return moved
    }

    private fun toRecord(
        group: PermissionGroup,
        createdAt: Instant,
        updatedAt: Instant,
        version: Long,
    ) = PermissionGroupRecord(
        id = group.id,
        name = group.name,
        weight = group.weight,
        isDefault = group.isDefault,
        chatPrefix = group.display.chatPrefix,
        chatSuffix = group.display.chatSuffix,
        nameColor = group.display.nameColor,
        tabPrefix = group.display.tabPrefix,
        permissions = group.permissions.map { (node, description) -> PermissionRecord(node, description) }.toSet(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )

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
