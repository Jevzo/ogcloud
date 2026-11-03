package io.ogwars.cloud.bukkit

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TickSamplerTest {
    private var now = 0L
    private val sampler = TickSampler { now }

    private fun tick(
        millis: Long,
        took: Double,
    ) {
        now += millis * 1_000_000
        sampler.record(took)
    }

    @Test
    fun `a server without ticks yet reports full speed`() {
        sampler.tps() shouldBe 20.0
        sampler.mspt() shouldBe 0.0
    }

    @Test
    fun `ticks every 50 ms are 20 TPS, slower ones less, and never more than 20`() {
        repeat(10) { tick(50, 12.0) }
        sampler.tps() shouldBe (20.0 plusOrMinus 0.001)
        sampler.mspt() shouldBe 12.0

        repeat(200) { tick(100, 90.0) }
        sampler.tps() shouldBe (10.0 plusOrMinus 0.001)
        sampler.mspt() shouldBe 90.0

        repeat(200) { tick(10, 5.0) }
        sampler.tps() shouldBe 20.0
    }
}
