package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.apitest.TestPasskeys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository

class LabelledCredentialsTest {
    private val stored = MapUserCredentialRepository()
    private val credentials = LabelledCredentials(stored)
    private val user = Bytes.random()
    private val id = Bytes.random()

    @Test
    fun `a new passkey's name is trimmed and checked as a rename is`() {
        credentials.save(TestPasskeys.record(user, "  Work laptop ", id))

        stored.findByCredentialId(id)?.label shouldBe "Work laptop"
        for (label in listOf(" ", "x".repeat(MAX_PASSKEY_LABEL + 1))) {
            shouldThrow<IllegalArgumentException> { credentials.save(TestPasskeys.record(user, label)) }
        }
        stored.findByUserId(user).size shouldBe 1
    }

    @Test
    fun `a sign-in saves a stored passkey as it is`() {
        val legacy = "x".repeat(MAX_PASSKEY_LABEL + 1)
        stored.save(TestPasskeys.record(user, legacy, id))

        credentials.save(TestPasskeys.record(user, legacy, id))

        stored.findByCredentialId(id)?.label shouldBe legacy
    }
}
