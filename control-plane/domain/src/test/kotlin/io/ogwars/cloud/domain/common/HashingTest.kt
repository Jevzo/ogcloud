package io.ogwars.cloud.domain.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HashingTest {
    @Test
    fun `sha256 of a known input`() {
        sha256Hex("abc") shouldBe "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }
}
