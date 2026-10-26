package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.ImageOverride
import io.ogwars.cloud.domain.group.ResourceSpec
import io.ogwars.cloud.domain.group.ScalingPolicy
import io.ogwars.cloud.domain.store.GroupStore
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Table("groups")
data class GroupRecord(
    @Id val id: String,
    val type: String,
    val profileId: String,
    val minOnline: Int,
    val maxInstances: Int,
    val playersPerServer: Int,
    val scaleUpThreshold: Double,
    val scaleDownThreshold: Double,
    val cooldownSeconds: Int,
    val drainTimeoutSeconds: Int,
    val maintenance: Boolean,
    val memoryRequest: String,
    val memoryLimit: String,
    val cpuRequest: String,
    val cpuLimit: String?,
    val jvmFlags: String,
    val imageOverride: String?,
    val imagePullSecretRef: String?,
    val storageSize: String?,
    val activeTemplateVersionId: UUID?,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    @Version val version: Long,
)

interface GroupRecordRepository : CrudRepository<GroupRecord, String> {
    @Query("select * from groups where deleted_at is null order by id")
    fun findAllActive(): List<GroupRecord>

    @Query("select * from groups where id = :id and deleted_at is null")
    fun findActive(id: String): GroupRecord?

    @Modifying
    @Query("update groups set deleted_at = :at, version = version + 1 where id = :id and deleted_at is null")
    fun softDelete(
        id: String,
        at: Instant,
    ): Int

    @Modifying
    @Query("update groups set deleted_at = null, version = version + 1 where id = :id")
    fun restore(id: String): Int

    @Query("select id from groups where deleted_at < :cutoff order by deleted_at")
    fun idsDeletedBefore(cutoff: Instant): List<String>
}

@Repository
class JdbcGroupStore(
    private val records: GroupRecordRepository,
    private val aggregates: JdbcAggregateTemplate,
    private val clock: Clock,
) : GroupStore {
    override fun findAll(): List<Group> = records.findAllActive().map(::toDomain)

    override fun find(id: GroupId): Group? = records.findActive(id.value)?.let(::toDomain)

    override fun create(group: Group) {
        val now = clock.instant()
        aggregates.insert(toRecord(group, createdAt = now, updatedAt = now, version = 0))
    }

    override fun update(group: Group) {
        val existing =
            records.findActive(group.id.value) ?: throw NoSuchElementException("Group ${group.id} does not exist")
        records.save(
            toRecord(group, createdAt = existing.createdAt, updatedAt = clock.instant(), version = existing.version),
        )
    }

    override fun softDelete(
        id: GroupId,
        at: Instant,
    ) {
        records.softDelete(id.value, at)
    }

    override fun restore(id: GroupId) {
        records.restore(id.value)
    }

    override fun deletedBefore(cutoff: Instant): List<GroupId> = records.idsDeletedBefore(cutoff).map(::GroupId)

    override fun purge(id: GroupId) = records.deleteById(id.value)

    private fun toDomain(record: GroupRecord) =
        Group(
            id = GroupId(record.id),
            type = GroupType.valueOf(record.type),
            profileId = record.profileId,
            scaling =
                ScalingPolicy(
                    minOnline = record.minOnline,
                    maxInstances = record.maxInstances,
                    playersPerServer = record.playersPerServer,
                    scaleUpThreshold = record.scaleUpThreshold,
                    scaleDownThreshold = record.scaleDownThreshold,
                    cooldown = Duration.ofSeconds(record.cooldownSeconds.toLong()),
                ),
            drainTimeout = Duration.ofSeconds(record.drainTimeoutSeconds.toLong()),
            maintenance = record.maintenance,
            resources = ResourceSpec(record.memoryRequest, record.memoryLimit, record.cpuRequest, record.cpuLimit),
            jvmFlags = record.jvmFlags,
            imageOverride = record.imageOverride?.let { ImageOverride(it, record.imagePullSecretRef) },
            storageSize = record.storageSize,
            activeTemplateVersionId = record.activeTemplateVersionId,
        )

    private fun toRecord(
        group: Group,
        createdAt: Instant,
        updatedAt: Instant,
        version: Long,
    ) = GroupRecord(
        id = group.id.value,
        type = group.type.name,
        profileId = group.profileId,
        minOnline = group.scaling.minOnline,
        maxInstances = group.scaling.maxInstances,
        playersPerServer = group.scaling.playersPerServer,
        scaleUpThreshold = group.scaling.scaleUpThreshold,
        scaleDownThreshold = group.scaling.scaleDownThreshold,
        cooldownSeconds =
            group.scaling.cooldown.seconds
                .toInt(),
        drainTimeoutSeconds = group.drainTimeout.seconds.toInt(),
        maintenance = group.maintenance,
        memoryRequest = group.resources.memoryRequest,
        memoryLimit = group.resources.memoryLimit,
        cpuRequest = group.resources.cpuRequest,
        cpuLimit = group.resources.cpuLimit,
        jvmFlags = group.jvmFlags,
        imageOverride = group.imageOverride?.image,
        imagePullSecretRef = group.imageOverride?.pullSecretRef,
        storageSize = group.storageSize,
        activeTemplateVersionId = group.activeTemplateVersionId,
        deletedAt = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )
}
