package io.ogwars.cloud.persistence

import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/** Applies the migrations the way the API does (D-0009), up to [target] when given. */
fun migrate(
    dataSource: DataSource,
    target: String? = null,
) {
    Flyway
        .configure()
        .dataSource(dataSource)
        .placeholders(mapOf("controllerRole" to PostgresContainerConfiguration.CONTROLLER_ROLE))
        .apply { target?.let(::target) }
        .load()
        .migrate()
}

/** A data source for the container's database, with the container's own credentials. */
fun PostgreSQLContainer.dataSource(): DataSource =
    PGSimpleDataSource().apply {
        setUrl(jdbcUrl)
        user = username
        password = this@dataSource.password
    }

/** One migrated database per test JVM, for tests that use stores without a Spring context. */
object TestDatabase {
    val jdbc: JdbcClient by lazy {
        val container =
            PostgreSQLContainer(
                PostgresContainerConfiguration.POSTGRES_IMAGE,
            ).withInitScript("db/test-init.sql").also {
                it.start()
            }
        val dataSource = container.dataSource()
        migrate(dataSource)
        JdbcClient.create(dataSource)
    }
}
