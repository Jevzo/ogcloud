package io.ogwars.cloud.persistence

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/** Boot entry point for the slice tests; the library itself has no application. */
@SpringBootApplication
class PersistenceTestApplication

@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer =
        PostgreSQLContainer(POSTGRES_IMAGE)
            // Creates the controller role the migration grants data access to.
            .withInitScript("db/test-init.sql")

    companion object {
        /** Same major as the CloudNativePG cluster (D-0067). */
        const val POSTGRES_IMAGE = "postgres:18.6"
    }
}
