package io.ogwars.cloud.api

import io.ogwars.cloud.api.web.Cursors
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CursorsTest {
    @Test
    fun `cursors round-trip and reject garbage`() {
        val id = UUID.fromString("0192d2c1-1a2b-7c3d-8e4f-123456789abc")

        Cursors.decode(Cursors.encode(id)) shouldBe id
        shouldThrow<IllegalArgumentException> { Cursors.decode("not a cursor!") }
        shouldThrow<IllegalArgumentException> { Cursors.decode("AAAA") }
    }

    @Test
    fun `a page reports a next cursor only when more rows exist`() {
        val ids = List(3) { UUID(0, it.toLong()) }

        Cursors.page(ids, limit = 2) { it }.nextCursor shouldBe Cursors.encode(ids[1])
        Cursors.page(ids.take(2), limit = 2) { it }.nextCursor shouldBe null
    }

    @Test
    fun `limits are bounded`() {
        Cursors.limit(null) shouldBe Cursors.DEFAULT_LIMIT
        shouldThrow<IllegalArgumentException> { Cursors.limit(0) }
        shouldThrow<IllegalArgumentException> { Cursors.limit(Cursors.MAX_LIMIT + 1) }
    }
}
