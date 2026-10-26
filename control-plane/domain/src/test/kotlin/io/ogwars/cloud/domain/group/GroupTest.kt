package io.ogwars.cloud.domain.group

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration

class GroupTest {
    @ParameterizedTest
    @ValueSource(strings = ["lobby", "staff-lobby", "bw4x4", "a"])
    fun `accepts DNS-safe group ids, including dashes`(id: String) {
        GroupId(id).value shouldBe id
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "Lobby", "-lobby", "lobby-", "4lobby", "lobby_1", "a-very-long-group-name-over-thirty"])
    fun `rejects ids that would break pod names`(id: String) {
        shouldThrow<IllegalArgumentException> { GroupId(id) }
    }

    @Test
    fun `a static group runs at most one server`() {
        shouldThrow<IllegalArgumentException> {
            group(GroupType.STATIC, scaling(min = 1, max = 2))
        }
    }

    @Test
    fun `scale-down threshold must sit below scale-up`() {
        shouldThrow<IllegalArgumentException> { scaling(up = 0.5, down = 0.6) }
    }

    @Test
    fun `maxInstances must cover minOnline`() {
        shouldThrow<IllegalArgumentException> { scaling(min = 3, max = 2) }
    }
}
