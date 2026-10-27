package io.ogwars.cloud.api.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException

class SecretCipherTest {
    private val oldKey = key(1)
    private val newKey = key(2)
    private val secret = "a totp seed".toByteArray()
    private val alice = "alice".toByteArray()

    @Test
    fun `secrets round-trip and differ on every seal`() {
        val cipher = cipher(active = "new")

        val first = cipher.seal(secret, alice)
        val second = cipher.seal(secret, alice)

        cipher.open(first, alice) shouldBe secret
        first.keyId shouldBe "new"
        first.bytes.toList() shouldNotBe second.bytes.toList()
    }

    @Test
    fun `a secret moved to another user does not open`() {
        val cipher = cipher(active = "new")

        shouldThrow<AEADBadTagException> { cipher.open(cipher.seal(secret, alice), "bob".toByteArray()) }
    }

    @Test
    fun `secrets sealed under an older key still open after rotation`() {
        val sealed = cipher(active = "old").seal(secret, alice)

        cipher(active = "new").open(sealed, alice) shouldBe secret
    }

    @Test
    fun `keys must be AES-256 and the active one must exist`() {
        shouldThrow<IllegalArgumentException> {
            SecretCipher.of(
                AuthProperties.Totp(mapOf("short" to Base64.getEncoder().encodeToString(ByteArray(16))), "short"),
                SecureRandom(),
            )
        }
        shouldThrow<IllegalArgumentException> { cipher(active = "missing") }
    }

    private fun cipher(active: String) =
        SecretCipher.of(AuthProperties.Totp(mapOf("old" to oldKey, "new" to newKey), active), SecureRandom())

    private fun key(fill: Int) = Base64.getEncoder().encodeToString(ByteArray(KEY_BYTES) { fill.toByte() })

    private companion object {
        const val KEY_BYTES = 32
    }
}
