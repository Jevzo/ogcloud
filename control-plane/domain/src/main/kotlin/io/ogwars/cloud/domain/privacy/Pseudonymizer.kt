package io.ogwars.cloud.domain.privacy

import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Replaces personal identifiers in records that must outlive an erasure, such as audit entries
 * (D-0049). The same input and key always give the same pseudonym, so an erased person's
 * entries still group together without revealing who they were.
 */
class Pseudonymizer(
    key: ByteArray,
) {
    private val keySpec: SecretKeySpec

    init {
        require(key.size >= MIN_KEY_BYTES) { "Pseudonym key must have at least $MIN_KEY_BYTES bytes" }
        keySpec = SecretKeySpec(key.copyOf(), ALGORITHM)
    }

    fun pseudonym(identifier: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(keySpec) }
        val digest = mac.doFinal(identifier.toByteArray(Charsets.UTF_8))
        return PREFIX + HexFormat.of().formatHex(digest, 0, PSEUDONYM_BYTES)
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val MIN_KEY_BYTES = 32
        const val PSEUDONYM_BYTES = 12
        const val PREFIX = "erased-"
    }
}
