package io.ogwars.cloud.bukkit

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ForwardingHandshakeTest {
    private val player = UUID.fromString("0192d2c1-1a2b-7c3d-8e4f-123456789abc")
    private val skin = """{"name":"textures","value":"abc","signature":"sig"}"""

    private fun handshake(vararg properties: String) =
        listOf("lobby.example", "203.0.113.7", player.toString().replace("-", ""), "[${properties.joinToString(",")}]")
            .joinToString("\u0000")

    private fun token(value: String) = """{"name":"bungeeguard-token","value":"$value"}"""

    @Test
    fun `a login with a known token is taken, without the token in its properties`() {
        val verdict = ForwardingHandshake.verify(handshake(skin, token("new")), setOf("old", "new"))

        val login = verdict.shouldBeInstanceOf<HandshakeVerdict.Accepted>().login
        login.uuid shouldBe player
        login.address shouldBe "203.0.113.7"
        login.propertiesJson shouldBe "[$skin]"
    }

    @Test
    fun `missing, doubled or unknown tokens and broken handshakes are refused`() {
        listOf(
            handshake(skin),
            handshake(token("new"), token("new")),
            handshake(token("stolen")),
            "lobby.example",
            handshake(skin).replace("[", "{"),
        ).forEach { ForwardingHandshake.verify(it, setOf("new")).shouldBeInstanceOf<HandshakeVerdict.Refused>() }
    }

    @Test
    fun `tokens are read again once they are older than the cache`() {
        var now = Instant.parse("2026-11-03T12:00:00Z")
        var content = "old\n"
        var reads = 0
        val clock =
            object : Clock() {
                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId?) = this

                override fun instant() = now
            }
        val tokens =
            ForwardingTokens(Path.of("tokens"), clock, Duration.ofSeconds(5)) {
                reads++
                content
            }

        tokens.current() shouldBe setOf("old")
        content = "old\nnew\n"
        tokens.current() shouldBe setOf("old")
        now = now.plusSeconds(5)
        tokens.current() shouldBe setOf("old", "new")
        reads shouldBe 2
    }
}
