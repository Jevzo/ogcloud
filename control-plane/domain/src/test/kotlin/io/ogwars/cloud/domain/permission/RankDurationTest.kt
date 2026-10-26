package io.ogwars.cloud.domain.permission

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration

class RankDurationTest {
    @Test
    fun `minus one is permanent`() {
        RankDuration.parse("-1") shouldBe RankDuration.Permanent
        RankDuration.format(RankDuration.Permanent) shouldBe "-1"
    }

    @ParameterizedTest
    @CsvSource("30d, 2592000", "1h 30m, 5400", "1w, 604800", "1M, 2629800", "1Y, 31557600", "' 2d   5s ', 172805")
    fun `parses every unit with v1 lengths`(
        text: String,
        seconds: Long,
    ) {
        RankDuration.parse(text) shouldBe RankDuration.Timed(Duration.ofSeconds(seconds))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "5", "5x", "-5d", "0d", "d", "99999999999999999Y"])
    fun `rejects malformed durations`(text: String) {
        shouldThrow<IllegalArgumentException> { RankDuration.parse(text) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1Y 2M 3w 4d 5h 6m 7s", "30d", "1h 30m"])
    fun `format reverses parse`(text: String) {
        RankDuration.format(RankDuration.parse(text)) shouldBe text.replace("30d", "4w 2d")
    }
}
