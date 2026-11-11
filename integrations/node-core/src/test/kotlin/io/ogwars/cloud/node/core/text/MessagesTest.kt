package io.ogwars.cloud.node.core.text

import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.toLegacy
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
    fun `every text is a valid tag text using exactly its declared placeholders`() {
        Message.entries.forEach { message ->
            withClue(message.key) {
                RichText.parseValid(bundle.getString(message.key)).placeholders() shouldBe message.placeholders
            }
        }
    }

    @Test
    fun `values are literal text, filled once`() {
        val messages = Messages.load()

        messages.text(Message.PLAYER_TRANSFERRING, "player" to "<red>Steve", "target" to "{player}").toLegacy() shouldBe
            "§aSending <red>Steve to {player}."
        messages.text(Message.GROUP_LIST_MAINTENANCE).toLegacy() shouldBe " §c[MAINT]"
    }

    @Test
    fun `a value that is itself a text keeps its formatting`() {
        val messages = Messages.load()

        messages
            .text(
                Message.PERM_GROUP_INFO_PREFIX,
                "prefix" to RichText.parseValid("<gold>[VIP]"),
            ).toLegacy() shouldBe
            "§7Prefix: §6[VIP]"
    }

    @Test
    fun `missing or extra values are a programming error`() {
        val messages = Messages.load()

        shouldThrow<IllegalArgumentException> { messages.text(Message.SERVER_NOT_FOUND) }
        shouldThrow<IllegalArgumentException> { messages.text(Message.NONE, "x" to 1) }
    }
}
