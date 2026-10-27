package io.ogwars.cloud.api.auth

import io.ogwars.cloud.apitest.TestSigningKeys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SigningKeysTest {
    @Test
    fun `every pem in the directory is loaded under its file name`(
        @TempDir dir: Path,
    ) {
        Files.writeString(dir.resolve("a.pem"), TestSigningKeys.pem(TestSigningKeys.rsa()))
        Files.writeString(dir.resolve("b.pem"), TestSigningKeys.pem(TestSigningKeys.rsa()))
        Files.writeString(dir.resolve("README"), "not a key")
        Files.createDirectory(dir.resolve("..data"))

        val keys = SigningKeys.load(dir, activeKeyId = "b")

        keys.keys.map { it.keyID } shouldContainExactly listOf("a", "b")
        keys.keys.all { it.isPrivate } shouldBe true
        keys.activeKeyId shouldBe "b"
    }

    @Test
    fun `the active key must exist`(
        @TempDir dir: Path,
    ) {
        Files.writeString(dir.resolve("a.pem"), TestSigningKeys.pem(TestSigningKeys.rsa()))

        shouldThrow<IllegalArgumentException> { SigningKeys.load(dir, activeKeyId = "z") }.message shouldContain "'z'"
    }

    @Test
    fun `an empty directory is refused`(
        @TempDir dir: Path,
    ) {
        shouldThrow<IllegalArgumentException> { SigningKeys.load(dir, activeKeyId = "a") }.message shouldContain
            "No signing keys"
    }

    @Test
    fun `weak and PKCS#1 keys are refused`() {
        shouldThrow<IllegalArgumentException> {
            SigningKeys.parse("weak", TestSigningKeys.pem(TestSigningKeys.rsa(bits = WEAK_BITS)))
        }.message shouldContain "2048"
        val pkcs1 = TestSigningKeys.pem(TestSigningKeys.rsa()).replace("PRIVATE KEY", "RSA PRIVATE KEY")
        shouldThrow<IllegalArgumentException> { SigningKeys.parse("old-format", pkcs1) }.message shouldContain "PKCS#8"
    }

    private companion object {
        const val WEAK_BITS = 1024
    }
}
