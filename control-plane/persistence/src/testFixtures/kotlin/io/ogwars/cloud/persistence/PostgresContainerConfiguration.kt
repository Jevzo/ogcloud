package io.ogwars.cloud.persistence

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = postgresContainer()

    companion object {
        /** PostgreSQL at the pinned image, with the controller role the migration grants data access to. */
        fun postgresContainer(): PostgreSQLContainer =
            PostgreSQLContainer(POSTGRES_IMAGE).withInitScript("db/test-init.sql")

        /** Pinned in testing/images.properties (D-0113). */
        val POSTGRES_IMAGE: String = System.getProperty("ogcloud.image.postgres")

        /** Matches the `controllerRole` Flyway placeholder. */
        const val CONTROLLER_ROLE = "ogcloud_controller"
    }
}
