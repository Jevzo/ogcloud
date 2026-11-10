package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.common.REDACTED
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class SecretsInToStringTest {
    @Test
    fun `the Valkey settings never show the password`() {
        val properties = ValkeyProperties(host = "valkey", username = "ogcloud-api", password = SECRET)
        listOf(properties, properties.toSettings()).forEach {
            it.toString() shouldNotContain SECRET
            it.toString() shouldContain REDACTED
        }
    }

    private companion object {
        const val SECRET = "s3cret-marker"
    }
}
