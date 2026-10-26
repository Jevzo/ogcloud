package io.ogwars.cloud.persistence

import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/** Applies the migrations the way the API does (D-0009). */
fun migrate(dataSource: DataSource) {
    Flyway
        .configure()
        .dataSource(dataSource)
        .placeholders(mapOf("controllerRole" to PostgresContainerConfiguration.CONTROLLER_ROLE))
        .load()
        .migrate()
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
        val dataSource =
            PGSimpleDataSource().apply {
                setUrl(container.jdbcUrl)
                user = container.username
                password = container.password
            }
        migrate(dataSource)
        JdbcClient.create(dataSource)
    }
}
