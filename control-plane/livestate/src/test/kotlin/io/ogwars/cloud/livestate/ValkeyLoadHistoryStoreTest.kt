package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.overview.GroupLoad
import io.ogwars.cloud.domain.overview.LoadSample
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class ValkeyLoadHistoryStoreTest : ValkeyTest() {
    private val history = ValkeyLoadHistoryStore(valkey)
    private val kept = Duration.ofHours(24)

    private fun sample(
        minutesAgo: Long,
        players: Int,
    ) = LoadSample(
        at = NOW.minus(Duration.ofMinutes(minutesAgo)),
        players = players,
        groups =
            mapOf(
                GroupId("lobby") to GroupLoad(running = 2, pending = 1, players = players),
                GroupId("idle") to GroupLoad.NONE,
            ),
    )

    @Test
    fun `samples come back oldest first, groups and all, from the requested time on`() {
        val samples = listOf(sample(minutesAgo = 2, players = 10), sample(minutesAgo = 1, players = 12), sample(0, 9))
        samples.reversed().forEach { history.record(it, kept) }

        history.since(NOW.minus(Duration.ofMinutes(5))) shouldBe samples
        history.since(NOW.minus(Duration.ofMinutes(1))) shouldBe samples.drop(1)
    }

    @Test
    fun `each write forgets samples older than the window and lets the key expire with it`() {
        history.record(sample(minutesAgo = 25 * 60, players = 1), kept)
        history.record(sample(minutesAgo = 0, players = 2), kept)

        history.since(NOW.minus(Duration.ofDays(2))).map { it.players } shouldBe listOf(2)
        valkey.sync().pttl(ValkeyKeys.LOAD_HISTORY) shouldBeInRange (kept.toMillis() - 60_000)..kept.toMillis()
    }

    @Test
    fun `an unreadable sample is left out, not the whole day`() {
        history.record(sample(minutesAgo = 2, players = 10), kept)
        val oneMinuteAgo = NOW.minus(Duration.ofMinutes(1)).toEpochMilli().toDouble()
        valkey.sync().zadd(ValkeyKeys.LOAD_HISTORY, oneMinuteAgo, NOT_A_SAMPLE)
        history.record(sample(minutesAgo = 0, players = 9), kept)

        history.since(NOW.minus(Duration.ofMinutes(5))).map { it.players } shouldBe listOf(10, 9)
    }

    private companion object {
        // Wire type 7 does not exist, so no protobuf parser reads it.
        val NOT_A_SAMPLE = byteArrayOf(-1, -1, -1)
    }
}
