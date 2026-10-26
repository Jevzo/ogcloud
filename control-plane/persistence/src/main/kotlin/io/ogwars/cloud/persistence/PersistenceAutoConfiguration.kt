package io.ogwars.cloud.persistence

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

/** The persistence ports on the service's DataSource (D-0074); the service provides the `Clock`. */
@AutoConfiguration
@EnableJdbcRepositories(basePackageClasses = [PersistenceAutoConfiguration::class])
@Import(PersistenceStores::class)
class PersistenceAutoConfiguration

/** Every store, also imported by the module's test slice. */
@Configuration(proxyBeanMethods = false)
@Import(
    JsonCodec::class,
    JdbcGroupStore::class,
    JdbcNetworkSettingsStore::class,
    JdbcPermissionGroupStore::class,
    JdbcTemplateVersionStore::class,
    JdbcPlayerStore::class,
    JdbcNpcStore::class,
    JdbcJobStore::class,
    JdbcAuditLogStore::class,
    JdbcScalingLogStore::class,
    JdbcRetentionStore::class,
    JdbcRuntimeArtifactStore::class,
    SchemaVersionProbe::class,
)
class PersistenceStores
