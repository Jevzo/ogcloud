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
const val TEST_TOTP_KEY_ID = "k1"

/** 32 zero bytes, base64: fine for tests, never for an installation. */
const val TEST_TOTP_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
const val UNLIMITED = 1_000_000

/** The full API against real PostgreSQL, Valkey and Kafka; one cached context for every API test. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // Static values, known before the web server and its filters start.
    properties = [
        "ogcloud.auth.setup-token=$TEST_SETUP_TOKEN",
        "ogcloud.auth.totp.keys.$TEST_TOTP_KEY_ID=$TEST_TOTP_KEY",
        "ogcloud.auth.totp.active-key-id=$TEST_TOTP_KEY_ID",
        // Every API test signs in from 127.0.0.1; RateLimitFilterTest covers the limits themselves.
        "ogcloud.rate-limits.auth.capacity=$UNLIMITED",
        "ogcloud.rate-limits.auth.per-minute=$UNLIMITED",
        "ogcloud.rate-limits.api.capacity=$UNLIMITED",
        "ogcloud.rate-limits.api.per-minute=$UNLIMITED",
    ],
)
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
            // Object storage is not touched by these tests; the client only needs valid settings.
            registry.add("ogcloud.s3.endpoint") { "http://localhost:1" }
            registry.add("ogcloud.s3.region") { "test" }
            registry.add("ogcloud.s3.bucket") { "ogcloud" }
            registry.add("ogcloud.s3.access-key") { "test" }
            registry.add("ogcloud.s3.secret-key") { "test" }
            registry.add("ogcloud.s3.path-style") { "true" }
        }
}
