package io.ogwars.cloud.domain.crashloop

import io.ogwars.cloud.domain.server.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class CrashLoopPolicyTest {
    private val policy = CrashLoopPolicy()

    @Test
    fun `no recent failures is healthy`() {
        policy.status(emptyList(), T0) shouldBe CrashLoopStatus.Healthy
        policy.status(listOf(T0.minus(Duration.ofMinutes(11))), T0) shouldBe CrashLoopStatus.Healthy
    }

    @Test
    fun `backoff doubles with each failure`() {
        policy.status(listOf(T0), T0) shouldBe CrashLoopStatus.BackingOff(T0.plusSeconds(15))
        policy.status(listOf(T0.minusSeconds(5), T0), T0) shouldBe CrashLoopStatus.BackingOff(T0.plusSeconds(30))
        policy.status(listOf(T0.minusSeconds(60)), T0) shouldBe CrashLoopStatus.Healthy
    }

    @Test
    fun `five failures within ten minutes pause the group`() {
        val failures = (0L..4L).map { T0.minus(Duration.ofMinutes(it * 2)) }

        policy.status(failures, T0) shouldBe CrashLoopStatus.Paused
    }

    @Test
    fun `backoff is capped`() {
        val capped = CrashLoopPolicy(CrashLoopSettings(pauseAfter = 50, maxBackoff = Duration.ofMinutes(1)))
        val failures = (0L..9L).map { T0.minusSeconds(it) }

        capped.status(failures, T0) shouldBe CrashLoopStatus.BackingOff(T0.plus(Duration.ofMinutes(1)))
    }
}
