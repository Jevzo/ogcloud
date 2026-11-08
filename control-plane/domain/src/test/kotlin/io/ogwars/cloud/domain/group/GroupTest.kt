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

    @ParameterizedTest
    @ValueSource(strings = ["", "-Xmx2G", "  -Xms1G   -XX:+UseZGC -Dfile.encoding=UTF-8 ", "-Dlist=a*b?"])
    fun `JVM flags are plain flags separated by spaces`(flags: String) {
        group().copy(jvmFlags = flags).jvmFlags shouldBe flags
    }

    @ParameterizedTest
    @ValueSource(strings = ["Xmx2G", "-Dname='a b'", "-Dname=\"x\"", "-Dhome=\$HOME", "-Dx=`id`", "-Dpath=C:\\x"])
    fun `JVM flags the entrypoint would mangle are refused (D-0143)`(flags: String) {
        shouldThrow<IllegalArgumentException> { group().copy(jvmFlags = flags) }
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
