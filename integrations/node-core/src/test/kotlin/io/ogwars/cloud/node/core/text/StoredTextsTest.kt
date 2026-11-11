package io.ogwars.cloud.node.core.text

import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.TextField
import io.ogwars.cloud.text.toLegacy
import io.ogwars.cloud.text.toPlain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

class StoredTextsTest {
    private val texts = StoredTexts()

    @Test
    fun `a valid text renders with its tags, parsed once`() {
        texts.parse("<red>Hi").toLegacy() shouldBe "§cHi"
        texts.parse("<red>Hi") shouldBeSameInstanceAs texts.parse("<red>Hi")
    }

    @Test
    fun `a text from before a rule is shown as written instead of failing (D-0205)`() {
        texts.parse("a < b").toPlain() shouldBe "a < b"
        texts.parse("<frog>").toPlain() shouldBe "<frog>"
        val huge = "<red>".repeat(TextField.MAX_LENGTH)
        texts.parse(huge) shouldBe RichText.plain(huge)
    }
}
