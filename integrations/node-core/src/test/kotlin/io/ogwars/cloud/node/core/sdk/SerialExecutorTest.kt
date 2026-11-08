package io.ogwars.cloud.node.core.sdk

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SerialExecutorTest {
    private val drains = ArrayDeque<Runnable>()
    private val serial = SerialExecutor { drains += it }

    @Test
    fun `tasks run one at a time, in the order they came, also those queued while one runs`() {
        val ran = mutableListOf<Int>()
        serial.execute {
            ran += 1
            serial.execute { ran += 3 }
        }
        serial.execute { ran += 2 }

        drains.size shouldBe 1
        drains.removeFirst().run()

        ran shouldContainExactly listOf(1, 2, 3)
        drains.size shouldBe 0
    }

    @Test
    fun `a failing task does not stop the ones after it`() {
        val ran = mutableListOf<Int>()
        serial.execute { error("a broken listener") }
        serial.execute { ran += 2 }

        runCatching { drains.removeFirst().run() }
        drains.removeFirstOrNull()?.run()

        ran shouldContainExactly listOf(2)
    }
}
