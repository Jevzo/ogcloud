package io.ogwars.cloud.node.core.text

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.ResourceBundle

class MessagesTest {
    private val bundle = ResourceBundle.getBundle("io.ogwars.cloud.node.core.messages", Locale.ENGLISH)

    @Test
    fun `the catalog holds exactly the declared keys`() {
        bundle.keySet() shouldContainExactlyInAnyOrder Message.entries.map { it.key }
    }

    @Test
    fun `every text uses exactly its declared placeholders`() {
        Message.entries.forEach { message ->
            val used =
                Messages.PLACEHOLDER
                    .findAll(bundle.getString(message.key))
                    .map { it.groupValues[1] }
                    .toSet()
            withClue(message.key) { used shouldBe message.placeholders }
        }
    }

    @Test
    fun `placeholders are filled once, so values stay as they are`() {
        val messages = Messages.load()

        messages.text(Message.PLAYER_TRANSFERRING, "player" to "Steve", "target" to "{player}") shouldBe
            "&aSending Steve to {player}."
        messages.text(Message.GROUP_LIST_MAINTENANCE) shouldBe " &c[MAINT]"
    }

    @Test
    fun `missing or extra values are a programming error`() {
        val messages = Messages.load()

        shouldThrow<IllegalArgumentException> { messages.text(Message.SERVER_NOT_FOUND) }
        shouldThrow<IllegalArgumentException> { messages.text(Message.NONE, "x" to 1) }
    }
}
