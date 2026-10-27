package io.ogwars.cloud.api.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class PasswordsTest {
    private val encoder = Passwords.encoder()

    @Test
    fun `passwords are hashed with bcrypt`() {
        val hash = encoder.hash("correct horse battery staple")

        hash shouldStartWith "{bcrypt}"
        encoder.matches("correct horse battery staple", hash) shouldBe true
    }

    @Test
    fun `an account without a password matches nothing`() {
        encoder.matches("", Passwords.NO_PASSWORD) shouldBe false
        encoder.matches("anything at all", Passwords.NO_PASSWORD) shouldBe false
    }

    @Test
    fun `other hash formats are refused`() {
        shouldThrow<IllegalArgumentException> { encoder.matches("secret", "{noop}secret") }
    }
}
