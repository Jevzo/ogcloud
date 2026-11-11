package io.ogwars.cloud.text

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JsonTest {
    @Test
    fun `runs become the extra of an empty root, named colours by name and others as hex`() {
        RichText.parseValid("<red>a<bold>b</bold></red><#123456>\"c\\").toJson() shouldBe
            """{"text":"","extra":[{"text":"a","color":"red"},{"text":"b","color":"red","bold":true},""" +
            """{"text":"\"c\\","color":"#123456"}]}"""
    }

    @Test
    fun `an empty text and control characters stay valid JSON`() {
        RichText.plain("").toJson() shouldBe """{"text":""}"""
        RichText.plain("a\nb").toJson() shouldBe """{"text":"","extra":[{"text":"a\u000ab"}]}"""
    }
}
