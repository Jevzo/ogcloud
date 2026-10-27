package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.StaleUpdateException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Table("network_settings")
data class NetworkSettingsRecord(
    @Id val id: Short,
    val maintenance: Boolean,
    val motd: String,
    val maintenanceMotd: String,
    val versionName: String,
    val maintenanceVersionName: String,
    val maxPlayers: Int,
    val defaultGroupId: String,
    val routingStrategy: String,
    val permissionSystemEnabled: Boolean,
    val tablistEnabled: Boolean,
    val tablistHeader: String,
    val tablistFooter: String,
    val maintenanceKickMessage: String,
    val eulaAcceptedAt: Instant?,
    val requireMinecraftLink: Boolean,
    @Version val version: Long,
    val updatedAt: Instant,
)

interface NetworkSettingsRecordRepository : CrudRepository<NetworkSettingsRecord, Short>

@Repository
class JdbcNetworkSettingsStore(
    private val records: NetworkSettingsRecordRepository,
    private val aggregates: JdbcAggregateTemplate,
    private val jdbc: JdbcClient,
    private val clock: Clock,
) : NetworkSettingsStore {
    override fun load(): Revisioned<NetworkSettings> = current().let { Revisioned(toDomain(it), it.version) }

    @Transactional
    override fun save(
        expectedVersion: Long,
        settings: NetworkSettings,
    ) {
        // After the lock, a group deleted meanwhile is seen as gone.
        if (lockNetworkSettings(jdbc) != settings.defaultGroupId.value) {
            val exists =
                jdbc
                    .sql("select exists (select 1 from groups where id = :id and deleted_at is null)")
                    .param("id", settings.defaultGroupId.value)
                    .query(Boolean::class.java)
                    .single()
            require(exists) { "The default group ${settings.defaultGroupId} does not exist" }
        }
        try {
            write(expectedVersion, settings)
        } catch (e: OptimisticLockingFailureException) {
            throw StaleUpdateException("Network settings changed since version $expectedVersion").apply { initCause(e) }
        }
    }

    private fun write(
        expectedVersion: Long,
        settings: NetworkSettings,
    ) {
        aggregates.update(
            NetworkSettingsRecord(
                id = SINGLETON_ID,
                maintenance = settings.maintenance,
                motd = settings.motd,
                maintenanceMotd = settings.maintenanceMotd,
                versionName = settings.versionName,
                maintenanceVersionName = settings.maintenanceVersionName,
                maxPlayers = settings.maxPlayers,
                defaultGroupId = settings.defaultGroupId.value,
                routingStrategy = settings.routingStrategy.name,
                permissionSystemEnabled = settings.permissionSystemEnabled,
                tablistEnabled = settings.tablistEnabled,
                tablistHeader = settings.tablistHeader,
                tablistFooter = settings.tablistFooter,
                maintenanceKickMessage = settings.maintenanceKickMessage,
                eulaAcceptedAt = settings.eulaAcceptedAt,
                requireMinecraftLink = settings.requireMinecraftLink,
                version = expectedVersion,
                updatedAt = clock.instant(),
            ),
        )
    }

    // The V1 migration seeds the row, so its absence is a broken database, not a normal case.
    private fun current(): NetworkSettingsRecord =
        records.findById(SINGLETON_ID).orElseThrow { IllegalStateException("network_settings row is missing") }

    private fun toDomain(record: NetworkSettingsRecord) =
        NetworkSettings(
            maintenance = record.maintenance,
            motd = record.motd,
            maintenanceMotd = record.maintenanceMotd,
            versionName = record.versionName,
            maintenanceVersionName = record.maintenanceVersionName,
            maxPlayers = record.maxPlayers,
            defaultGroupId = GroupId(record.defaultGroupId),
            routingStrategy = RoutingStrategy.valueOf(record.routingStrategy),
            permissionSystemEnabled = record.permissionSystemEnabled,
            tablistEnabled = record.tablistEnabled,
            tablistHeader = record.tablistHeader,
            tablistFooter = record.tablistFooter,
            maintenanceKickMessage = record.maintenanceKickMessage,
            eulaAcceptedAt = record.eulaAcceptedAt,
            requireMinecraftLink = record.requireMinecraftLink,
        )

    private companion object {
        const val SINGLETON_ID: Short = 1
    }
}

/** Locks the single settings row for the rest of the transaction and returns the default group's id. */
internal fun lockNetworkSettings(jdbc: JdbcClient): String =
    jdbc.sql("select default_group_id from network_settings for update").query(String::class.java).single()
