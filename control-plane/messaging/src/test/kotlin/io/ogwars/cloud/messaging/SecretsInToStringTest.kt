package io.ogwars.cloud.messaging

import io.ogwars.cloud.domain.common.REDACTED
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class SecretsInToStringTest {
    @Test
    fun `the Kafka settings never show the SCRAM password`() {
        val settings = KafkaSettings("kafka:9093", "api", ScramCredentials("ogcloud-api", SECRET))

        settings.toString() shouldNotContain SECRET
        settings.toString() shouldContain REDACTED
    }

    private companion object {
        const val SECRET = "s3cret-marker"
    }
}
