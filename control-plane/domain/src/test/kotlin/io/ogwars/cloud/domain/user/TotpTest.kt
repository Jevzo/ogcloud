package io.ogwars.cloud.domain.user

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class TotpTest {
    private val secret = "12345678901234567890".toByteArray()

    @Test
    fun `matches the RFC 6238 SHA-1 test vectors`() {
        val rfc = Totp(digits = 8)
        mapOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130",
        ).forEach { (seconds, code) -> rfc.code(secret, rfc.step(Instant.ofEpochSecond(seconds))) shouldBe code }
    }

    @Test
    fun `codes from the neighbouring steps pass, older ones and replays do not`() {
        val totp = Totp()
        val now = Instant.ofEpochSecond(1_800_000_000)
        val step = totp.step(now)

        totp.verify(secret, totp.code(secret, step - 1), now, lastUsedStep = null) shouldBe step - 1
        totp.verify(secret, totp.code(secret, step + 1), now, lastUsedStep = null) shouldBe step + 1
        totp.verify(secret, totp.code(secret, step - 2), now, lastUsedStep = null) shouldBe null
        totp.verify(secret, totp.code(secret, step), now, lastUsedStep = step) shouldBe null
    }

    @Test
    fun `malformed codes are refused`() {
        val totp = Totp()
        val now = Instant.ofEpochSecond(1_800_000_000)

        totp.verify(secret, "12345", now, null) shouldBe null
        totp.verify(secret, "abcdef", now, null) shouldBe null
    }

    @Test
    fun `base32 follows RFC 4648`() {
        mapOf(
            "" to "",
            "f" to "MY",
            "fo" to "MZXQ",
            "foo" to "MZXW6",
            "foob" to "MZXW6YQ",
            "fooba" to "MZXW6YTB",
            "foobar" to "MZXW6YTBOI",
        ).forEach { (input, encoded) ->
            Base32.encode(input.toByteArray()) shouldBe encoded
            Base32.decode(encoded.lowercase()).decodeToString() shouldBe input
        }
    }

    @Test
    fun `a passkey alone or a second factor satisfies the policy`() {
        fun requirement(
            vararg factors: Factor,
            owner: Boolean = false,
            totp: Boolean = false,
            passkey: Boolean = false,
        ) = MfaPolicy.requirement(factors.toSet(), owner, totp, passkey)

        requirement(Factor.PASSKEY, owner = true) shouldBe MfaRequirement.NONE
        requirement(Factor.PASSWORD, Factor.TOTP, owner = true, totp = true) shouldBe MfaRequirement.NONE
        requirement(Factor.PASSWORD, totp = true) shouldBe MfaRequirement.TOTP_CODE
        requirement(Factor.PASSWORD) shouldBe MfaRequirement.NONE
        requirement(Factor.PASSWORD, owner = true, passkey = true) shouldBe MfaRequirement.PASSKEY_SIGN_IN
        requirement(Factor.PASSWORD, owner = true) shouldBe MfaRequirement.ENROLLMENT
    }
}
