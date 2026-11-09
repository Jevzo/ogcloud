package io.ogwars.cloud.api.auth

import io.ogwars.cloud.livestate.ValkeyProperties
import io.ogwars.cloud.livestate.ValkeyTls
import io.ogwars.cloud.service.TestTls
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisPassword

class SessionConfigurationTest {
    @Test
    fun `logs in to Sentinels with the service's own user over TLS`() {
        val factory =
            SessionConfiguration().redisConnectionFactory(
                ValkeyProperties(
                    sentinel = ValkeyProperties.Sentinel("ogcloud", listOf("valkey-0:26379", "valkey-1:26379")),
                    username = "ogcloud-api",
                    password = "s3cret",
                    tls = ValkeyTls(TestTls.certificate.toString()),
                ),
            )

        val sentinels = factory.sentinelConfiguration
        sentinels?.sentinelUsername shouldBe "ogcloud-api"
        sentinels?.sentinelPassword shouldBe RedisPassword.of("s3cret")
        factory.isUseSsl shouldBe true
    }
}
