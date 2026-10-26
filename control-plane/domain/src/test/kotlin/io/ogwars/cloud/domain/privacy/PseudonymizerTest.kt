package io.ogwars.cloud.domain.privacy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class PseudonymizerTest {
    private val pseudonymizer = Pseudonymizer(ByteArray(32) { it.toByte() })

    @Test
    fun `is stable per identifier and hides it`() {
        val pseudonym = pseudonymizer.pseudonym("jerry@example.com")

        pseudonym shouldBe pseudonymizer.pseudonym("jerry@example.com")
        pseudonym shouldStartWith "erased-"
        pseudonym shouldNotContain "jerry"
        pseudonym.length shouldBe "erased-".length + 24
    }

    @Test
    fun `different identifiers or keys give different pseudonyms`() {
        pseudonymizer.pseudonym("a") shouldNotBe pseudonymizer.pseudonym("b")
        Pseudonymizer(ByteArray(32) { 7 }).pseudonym("a") shouldNotBe pseudonymizer.pseudonym("a")
    }

    @Test
    fun `rejects short keys`() {
        shouldThrow<IllegalArgumentException> { Pseudonymizer(ByteArray(16)) }
    }
}
