package io.ogwars.cloud.persistence

import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val NOW: Instant = Instant.parse("2026-10-26T12:00:00Z")

@TestConfiguration(proxyBeanMethods = false)
class FixedClockConfiguration {
    @Bean
    fun clock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)
}

/** One slice configuration for every store test, so Spring caches the context and its container. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfiguration::class, FixedClockConfiguration::class, PersistenceStores::class)
annotation class PersistenceTest
