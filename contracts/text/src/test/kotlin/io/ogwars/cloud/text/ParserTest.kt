package io.ogwars.cloud.text

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ParserTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '`',
        textBlock = """
        plain text                                  | plain text
        <red>red                                    | red
        <#FF8800>hex, any case                      | hex, any case
        <color:#ff8800>color tag                    | color tag
        <color:gold>named in a color tag            | named in a color tag
        <gradient:#ff0000:#00ff00:blue>stops</gradient> | stops
        <rainbow>rainbow                            | rainbow
        <bold><italic><underlined><strikethrough><obfuscated>all | all
        <red>a</color>b                             | ab
        <#ff8800>a</color>b                         | ab
        <red><bold>a<reset>b                        | ab
        <red>unclosed                               | unclosed
        \<not a tag> \{x} \\                        | <not a tag> {x} \
        a \b c                                      | a \b c
        {1} and { are text                          | {1} and { are text""",
    )
    fun `valid texts parse to their plain text`(
        source: String,
        plain: String,
    ) {
        RichText.parseValid(source).toPlain() shouldBe plain
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '`',
        textBlock = """
        a <frog>b                   | 2 | unknown tag <frog>
        a < b                       | 2 | '<' starts no tag; write \< for a literal <
        <#12345>x                   | 0 | unknown tag <#12345>
        <gradient:#ff0000>x         | 0 | <gradient:#ff0000> takes two or more colours
        <gradient:#ff0000:pink>x    | 0 | "pink" in <gradient:#ff0000:pink> is no colour
        <color>x                    | 0 | <color> takes one colour
        <color:red:blue>x           | 0 | <color:red:blue> takes one colour
        <rainbow:2>x                | 0 | <rainbow> takes no arguments
        x</bold>                    | 1 | </bold> closes no open tag""",
    )
    fun `invalid texts name each error and where it is`(
        source: String,
        offset: Int,
        reason: String,
    ) {
        RichText.parse(source) shouldBe ParseResult.Invalid(listOf(TextError(offset, reason)))
    }

    @Test
    fun `every error is reported at once`() {
        val result = RichText.parse("<frog>a</bold><gradient:red>")
        result.shouldBeInstanceOf<ParseResult.Invalid>().errors.map { it.offset } shouldBe listOf(0, 7, 14)
    }

    @Test
    fun `placeholders are listed and filled in as literal text`() {
        val text = RichText.parseValid("<red>{player}</red> joined {server}")

        text.placeholders() shouldBe setOf("player", "server")
        val filled = text.fill("player" to "<bold>Eve", "server" to "lobby-1")
        filled.toPlain() shouldBe "<bold>Eve joined lobby-1"
        filled.runs().none { Decoration.BOLD in it.decorations } shouldBe true
    }

    @Test
    fun `an unfilled placeholder shows as written`() {
        RichText.parseValid("hi {name}").toPlain() shouldBe "hi {name}"
    }
}
