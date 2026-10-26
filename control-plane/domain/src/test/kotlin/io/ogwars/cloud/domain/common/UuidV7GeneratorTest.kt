package io.ogwars.cloud.domain.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.SplittableRandom

class UuidV7GeneratorTest {
    private val instant = Instant.parse("2026-10-26T12:00:00.123Z")
    private val generator = UuidV7Generator(Clock.fixed(instant, ZoneOffset.UTC), SplittableRandom(1))

    @Test
    fun `encodes version 7, the RFC variant and the clock`() {
        val id = generator.next()

        id.version() shouldBe 7
        id.variant() shouldBe 2
        (id.mostSignificantBits ushr 16) shouldBe instant.toEpochMilli()
    }

    @Test
    fun `ids from later instants sort after earlier ones`() {
        val earlier = UuidV7Generator(Clock.fixed(instant, ZoneOffset.UTC), SplittableRandom(2)).next()
        val later = UuidV7Generator(Clock.fixed(instant.plusMillis(1), ZoneOffset.UTC), SplittableRandom(3)).next()

        (earlier.toString() < later.toString()) shouldBe true
    }

    @Test
    fun `compact hex drops the dashes`() {
        val id = generator.next()

        id.compactHex().length shouldBe 32
        id.compactHex() shouldBe id.toString().replace("-", "")
    }
}
