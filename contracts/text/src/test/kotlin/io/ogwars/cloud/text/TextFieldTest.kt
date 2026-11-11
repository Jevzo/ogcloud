package io.ogwars.cloud.text

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TextFieldTest {
    @Test
    fun `a field takes its own placeholders and no others`() {
        TextField.TABLIST_HEADER.validate("<gold>{onlinePlayers}/{maxPlayers} on {server}").shouldBeEmpty()
        TextField.CHAT_PREFIX.validate("<gold>[VIP] {server}") shouldBe
            listOf(TextError(12, "{server} is no placeholder here; this text offers none"))
        TextField.NPC_TITLE.validate("{name}") shouldBe
            listOf(TextError(0, "{name} is no placeholder here; this text offers {players}, {servers}"))
    }

    @Test
    fun `a name format must show the name`() {
        TextField.NAME_FORMAT.validate("<gradient:red:blue>{name}</gradient>").shouldBeEmpty()
        TextField.NAME_FORMAT.validate("<red>") shouldBe listOf(TextError(0, "this text must show {name}"))
    }

    @Test
    fun `parse errors come before placeholder checks`() {
        TextField.MOTD.validate("<frog>") shouldBe listOf(TextError(0, "unknown tag <frog>"))
    }

    @Test
    fun `every placeholder has a sample a preview fills in`() {
        TextField.entries.forEach { field -> field.required.forEach { field.samples.keys.contains(it) shouldBe true } }
        TextField.NAME_FORMAT.samples shouldBe mapOf(Placeholders.NAME to "Steve")
    }
}
