package io.ogwars.cloud.domain.common

import java.time.Clock
import java.util.UUID
import java.util.random.RandomGenerator

/** Source of new identifiers; injected so tests are deterministic (D-0064). */
fun interface IdGenerator {
    fun next(): UUID
}

/** RFC 9562 version 7 UUIDs: a millisecond timestamp followed by random bits, so ids sort by creation time. */
class UuidV7Generator(
    private val clock: Clock,
    private val random: RandomGenerator,
) : IdGenerator {
    override fun next(): UUID {
        val millis = clock.millis()
        require(millis in 0..MAX_TIMESTAMP) { "Clock is outside the UUIDv7 range: $millis" }
        val mostSignificant = (millis shl TIMESTAMP_SHIFT) or VERSION_7 or (random.nextLong() and RAND_A_MASK)
        val leastSignificant = VARIANT_RFC_9562 or (random.nextLong() and RAND_B_MASK)
        return UUID(mostSignificant, leastSignificant)
    }

    private companion object {
        const val TIMESTAMP_SHIFT = 16
        const val MAX_TIMESTAMP = (1L shl 48) - 1
        const val VERSION_7 = 0x7000L
        const val RAND_A_MASK = 0x0FFFL
        const val VARIANT_RFC_9562 = Long.MIN_VALUE
        const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    }
}

/** The 32-character lowercase hex form used in pod and resource names. */
fun UUID.compactHex(): String = toString().replace("-", "")
