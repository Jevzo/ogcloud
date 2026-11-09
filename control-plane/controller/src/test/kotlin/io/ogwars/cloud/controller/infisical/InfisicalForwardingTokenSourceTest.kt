package io.ogwars.cloud.controller.infisical

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InfisicalForwardingTokenSourceTest {
    private val secret = MemorySecret("old\n")
    private val source = InfisicalForwardingTokenSource(secret)

    @Test
    fun `a new token goes last, where proxies take it`() {
        source.add("new")

        secret.value shouldBe "old\nnew"
        source.tokens() shouldBe listOf("old", "new")
    }

    @Test
    fun `adding a token twice keeps one copy`() {
        source.add("old")

        secret.writes shouldBe 0
    }

    @Test
    fun `removing leaves the other tokens`() {
        source.add("new")
        source.remove("old")

        secret.value shouldBe "new"
    }

    @Test
    fun `the last token stays, or no proxy could reach a backend`() {
        shouldThrow<IllegalStateException> { source.remove("old") }

        secret.value shouldBe "old\n"
    }

    @Test
    fun `a missing secret has no tokens`() {
        InfisicalForwardingTokenSource(MemorySecret(null)).tokens() shouldBe emptyList()
    }

    private class MemorySecret(
        var value: String?,
    ) : InfisicalSecret {
        var writes = 0

        override fun read() = value

        override fun write(value: String) {
            writes++
            this.value = value
        }
    }
}
