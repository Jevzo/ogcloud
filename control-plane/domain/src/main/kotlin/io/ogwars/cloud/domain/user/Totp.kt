package io.ogwars.cloud.domain.user

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/** Time-based one-time codes per RFC 6238: HMAC-SHA1, 30-second steps (D-0084). */
class Totp(
    private val digits: Int = DIGITS,
) {
    init {
        require(digits in 1..MAX_DIGITS) { "TOTP codes have 1 to $MAX_DIGITS digits" }
    }

    fun step(at: Instant): Long = at.epochSecond / STEP_SECONDS

    fun code(
        secret: ByteArray,
        step: Long,
    ): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(secret, ALGORITHM)) }
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        // RFC 4226 dynamic truncation: the low nibble of the last byte picks four bytes.
        val offset = hash.last().toInt() and LOW_NIBBLE
        val binary = ByteBuffer.wrap(hash, offset, Int.SIZE_BYTES).int and Int.MAX_VALUE
        return (binary % POWERS[digits]).toString().padStart(digits, '0')
    }

    /**
     * The step [code] belongs to, accepting one step of clock drift either way, or null. A step at or before
     * [lastUsedStep] is refused, so a code works once.
     */
    fun verify(
        secret: ByteArray,
        code: String,
        at: Instant,
        lastUsedStep: Long?,
    ): Long? {
        if (code.length != digits || !code.all(Char::isDigit)) return null
        val now = step(at)
        return (now - DRIFT..now + DRIFT).firstOrNull { step ->
            (lastUsedStep == null || step > lastUsedStep) && constantTimeEquals(code(secret, step), code)
        }
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        const val DIGITS = 6
        const val STEP_SECONDS = 30L
        private const val DRIFT = 1L
        private const val ALGORITHM = "HmacSHA1"
        private const val LOW_NIBBLE = 0x0f
        private const val BASE = 10.0
        private const val MAX_DIGITS = 9
        private val POWERS = IntArray(MAX_DIGITS + 1) { BASE.pow(it).toInt() }
    }
}

/** RFC 4648 base32 without padding, the form authenticator apps expect secrets in. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BITS_PER_CHAR = 5
    private const val BYTE_BITS = 8
    private const val CHAR_MASK = 0x1f
    private const val BYTE_MASK = 0xff

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl BYTE_BITS) or (byte.toInt() and BYTE_MASK)
            bits += BYTE_BITS
            while (bits >= BITS_PER_CHAR) {
                bits -= BITS_PER_CHAR
                out.append(ALPHABET[(buffer shr bits) and CHAR_MASK])
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (BITS_PER_CHAR - bits)) and CHAR_MASK])
        return out.toString()
    }

    /** Accepts lower case, spaces and padding, as people type secrets. */
    fun decode(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in text.uppercase()) {
            if (char == ' ' || char == '=') continue
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "'$char' is not a base32 character" }
            buffer = (buffer shl BITS_PER_CHAR) or value
            bits += BITS_PER_CHAR
            if (bits >= BYTE_BITS) {
                bits -= BYTE_BITS
                out.write((buffer shr bits) and BYTE_MASK)
            }
        }
        return out.toByteArray()
    }
}
