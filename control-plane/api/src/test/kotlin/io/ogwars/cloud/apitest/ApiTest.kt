package io.ogwars.cloud.apitest

import io.ogwars.cloud.livestate.ValkeyContainerConfiguration
import io.ogwars.cloud.messaging.KafkaContainerConfiguration
import io.ogwars.cloud.persistence.PostgresContainerConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistrar

/** The issuer tokens carry in tests; nothing listens there, flows only read the redirects. */
const val TEST_ISSUER = "http://ogcloud.test"

const val TEST_SETUP_TOKEN = "test-setup-token"

/** The full API against real PostgreSQL, Valkey and Kafka; one cached context for every API test. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiTestConfiguration::class)
annotation class ApiTest

@TestConfiguration(proxyBeanMethods = false)
@Import(
    PostgresContainerConfiguration::class,
    ValkeyContainerConfiguration::class,
    KafkaContainerConfiguration::class,
    ProbeController::class,
)
class ApiTestConfiguration {
    @Bean
    fun apiTestProperties() =
        DynamicPropertyRegistrar { registry ->
            registry.add("ogcloud.auth.issuer") { TEST_ISSUER }
            registry.add("ogcloud.auth.signing.keys-dir") { TestSigningKeys.dir.toString() }
            registry.add("ogcloud.auth.signing.active-key-id") { TestSigningKeys.CURRENT }
            registry.add("ogcloud.auth.setup-token") { TEST_SETUP_TOKEN }
            // Object storage is not touched by these tests; the client only needs valid settings.
            registry.add("ogcloud.s3.endpoint") { "http://localhost:1" }
            registry.add("ogcloud.s3.region") { "test" }
            registry.add("ogcloud.s3.bucket") { "ogcloud" }
            registry.add("ogcloud.s3.access-key") { "test" }
            registry.add("ogcloud.s3.secret-key") { "test" }
            registry.add("ogcloud.s3.path-style") { "true" }
        }
}
