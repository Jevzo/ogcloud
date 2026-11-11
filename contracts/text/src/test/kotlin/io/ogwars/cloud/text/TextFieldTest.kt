package io.ogwars.cloud.text

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class TextFieldTest {
    @Test
    fun `a field takes its own placeholders and no others`() {
        TextField.TABLIST_HEADER.validate("<gold>{onlinePlayers}/{maxPlayers} on {server}").shouldBeEmpty()
        TextField.CHAT_PREFIX.validate("<gold>[VIP] {server}") shouldBe
            listOf(
                TextError(
                    TextErrorCode.UNKNOWN_PLACEHOLDER,
                    12,
                    8,
                    "{server} is no placeholder here; this text offers none",
                ),
            )
        TextField.NPC_TITLE.validate("{name}") shouldBe
            listOf(
                TextError(
                    TextErrorCode.UNKNOWN_PLACEHOLDER,
                    0,
                    6,
                    "{name} is no placeholder here; this text offers {players}, {servers}",
                ),
            )
    }

    @Test
    fun `a name format must show the name`() {
        TextField.NAME_FORMAT.validate("<gradient:red:blue>{name}</gradient>").shouldBeEmpty()
        TextField.NAME_FORMAT.validate("<red>") shouldBe
            listOf(TextError(TextErrorCode.MISSING_PLACEHOLDER, 5, 0, "this text must show {name}"))
        // Empty: the catalog's default name format.
        TextField.NAME_FORMAT.validate("").shouldBeEmpty()
    }

    @Test
    fun `parse errors come before placeholder checks`() {
        TextField.MOTD.validate("<frog>{x}") shouldBe
            listOf(TextError(TextErrorCode.UNKNOWN_TAG, 0, 6, "unknown tag <frog>"))
    }

    @Test
    fun `a section sign is refused with the tag errors, in order`() {
        TextField.MOTD.validate("§ca <frog>") shouldBe
            listOf(
                TextError(TextErrorCode.SECTION_SIGN, 0, 1, "'§' is a legacy code; write tags instead"),
                TextError(TextErrorCode.UNKNOWN_TAG, 4, 6, "unknown tag <frog>"),
            )
    }

    @Test
    fun `a text holds at most the limit`() {
        TextField.MOTD.validate("a".repeat(TextField.MAX_LENGTH)).shouldBeEmpty()
        TextField.MOTD.validate("<red>".repeat(TextField.MAX_LENGTH)) shouldBe
            listOf(
                TextError(
                    TextErrorCode.TOO_LONG,
                    TextField.MAX_LENGTH,
                    TextField.MAX_LENGTH * 4,
                    "a text holds at most 2048 characters",
                ),
            )
    }

    @Test
    fun `a valid text parses once into its tree`() {
        TextField.KICK_MESSAGE
            .parse("<red>Bye")
            .shouldBeInstanceOf<ParseResult.Valid>()
            .text shouldBe
            RichText.parseValid("<red>Bye")
        TextField.KICK_MESSAGE
            .parse("")
            .shouldBeInstanceOf<ParseResult.Valid>()
            .text shouldBe RichText.plain("")
    }

    @Test
    fun `fields that 1_8 caps or shows only as legacy text say so`() {
        TextField.TAB_PREFIX.collapsesOnLegacy shouldBe true
        TextField.TAB_PREFIX.legacyMaxLength shouldBe 14
        TextField.VERSION_NAME.legacyOnly shouldBe true
        TextField.MOTD.legacyOnly shouldBe false
        TextField.NAME_FORMAT.samples shouldBe mapOf(Placeholders.NAME to "Steve")
    }
}
