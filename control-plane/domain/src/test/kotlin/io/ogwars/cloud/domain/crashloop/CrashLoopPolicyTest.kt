package io.ogwars.cloud.domain.crashloop

import io.ogwars.cloud.domain.server.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class CrashLoopPolicyTest {
    private val policy = CrashLoopPolicy()

    @Test
    fun `no recent failures is healthy`() {
        policy.status(emptyList(), pausedByLatch = false, now = T0) shouldBe CrashLoopStatus.Healthy
        policy.status(listOf(T0.minus(Duration.ofMinutes(11))), pausedByLatch = false, now = T0) shouldBe
            CrashLoopStatus.Healthy
    }

    @Test
    fun `backoff doubles with each failure`() {
        policy.status(listOf(T0), pausedByLatch = false, now = T0) shouldBe
            CrashLoopStatus.BackingOff(T0.plusSeconds(15))
        policy.status(listOf(T0.minusSeconds(5), T0), pausedByLatch = false, now = T0) shouldBe
            CrashLoopStatus.BackingOff(T0.plusSeconds(30))
        policy.status(listOf(T0.minusSeconds(60)), pausedByLatch = false, now = T0) shouldBe CrashLoopStatus.Healthy
    }

    @Test
    fun `five failures within ten minutes ask for a pause`() {
        val failures = (0L..4L).map { T0.minus(Duration.ofMinutes(it * 2)) }

        policy.status(failures, pausedByLatch = false, now = T0) shouldBe CrashLoopStatus.PauseNow
    }

    @Test
    fun `a latched pause holds after the failures age out`() {
        policy.status(emptyList(), pausedByLatch = true, now = T0.plus(Duration.ofDays(1))) shouldBe
            CrashLoopStatus.Paused
    }

    @Test
    fun `backoff is capped`() {
        val capped = CrashLoopPolicy(CrashLoopSettings(pauseAfter = 50, maxBackoff = Duration.ofMinutes(1)))
        val failures = (0L..9L).map { T0.minusSeconds(it) }

        capped.status(failures, pausedByLatch = false, now = T0) shouldBe
            CrashLoopStatus.BackingOff(T0.plus(Duration.ofMinutes(1)))
    }
}
