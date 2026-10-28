package io.ogwars.cloud.persistence

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer =
        PostgreSQLContainer(POSTGRES_IMAGE)
            // Creates the controller role the migration grants data access to.
            .withInitScript("db/test-init.sql")

    companion object {
        /** Pinned in testing/images.properties (D-0113). */
        val POSTGRES_IMAGE: String = System.getProperty("ogcloud.image.postgres")

        /** Matches the `controllerRole` Flyway placeholder. */
        const val CONTROLLER_ROLE = "ogcloud_controller"
    }
}
