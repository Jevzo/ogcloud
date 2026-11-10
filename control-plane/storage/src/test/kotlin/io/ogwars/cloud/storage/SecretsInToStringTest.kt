package io.ogwars.cloud.storage

import io.ogwars.cloud.domain.common.REDACTED
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.URI

class SecretsInToStringTest {
    @Test
    fun `the S3 settings never show the secret key`() {
        val settings = S3Settings(URI.create("https://s3.example.com"), "fsn1", "ogcloud", "access", SECRET, false)

        settings.toString() shouldNotContain SECRET
        settings.toString() shouldContain REDACTED
    }

    private companion object {
        const val SECRET = "s3cret-marker"
    }
}
