package io.ogwars.cloud.text

import io.kotest.assertions.throwables.shouldThrow
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
        a <frog>b                   | UNKNOWN_TAG     | 2 | 6  | unknown tag <frog>
        a < b                       | LONE_ANGLE      | 2 | 1  | '<' starts no tag; write \< for a literal <
        <#12345>x                   | UNKNOWN_TAG     | 0 | 8  | unknown tag <#12345>
        <#-12345>x                  | UNKNOWN_TAG     | 0 | 9  | unknown tag <#-12345>
        <#+12345>x                  | UNKNOWN_TAG     | 0 | 9  | unknown tag <#+12345>
        <color:#-12345>x            | BAD_COLOUR      | 0 | 15 | "#-12345" in <color:#-12345> is no colour
        <gradient:#-12345:red>x     | BAD_COLOUR      | 0 | 22 | "#-12345" in <gradient:#-12345:red> is no colour
        <gradient:#ff0000>x         | GRADIENT_STOPS  | 0 | 18 | <gradient:#ff0000> takes two or more colours
        <gradient:#ff0000:pink>x    | BAD_COLOUR      | 0 | 23 | "pink" in <gradient:#ff0000:pink> is no colour
        <color>x                    | COLOUR_COUNT    | 0 | 7  | <color> takes one colour
        <color:red:blue>x           | COLOUR_COUNT    | 0 | 16 | <color:red:blue> takes one colour
        <rainbow:2>x                | TAG_ARGUMENTS   | 0 | 11 | <rainbow> takes no arguments
        <color:red>a</red>          | UNMATCHED_CLOSE | 12 | 6 | </red> closes no open tag
        x</bold>                    | UNMATCHED_CLOSE | 1 | 7  | </bold> closes no open tag""",
    )
    fun `invalid texts name each error, where it is and how long`(
        source: String,
        code: TextErrorCode,
        offset: Int,
        length: Int,
        reason: String,
    ) {
        RichText.parse(source) shouldBe ParseResult.Invalid(listOf(TextError(code, offset, length, reason)))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '`',
        textBlock = """
        <red><bold>a<reset>b      | a:red:BOLD,b::
        <bold><red>a</bold>b      | a:red:BOLD,b::
        <red><bold>a</red>b       | a:red:BOLD,b::
        <RED>a</RED>b             | a:red:,b::
        <red>a<#123456>b</color>c | a:red:,b:#123456:,c:red:
        <red>a</color>b           | a:red:,b::
        <gradient:red:blue>a</gradient>b | a:red:,b::""",
    )
    fun `closing tags end what they close and everything opened inside`(
        source: String,
        spans: String,
    ) {
        RichText.parseValid(source).spans().joinToString(",") { span ->
            val color = (span.color as? NamedColor)?.tag ?: span.color?.hex.orEmpty()
            "${span.text}:$color:${span.decorations.joinToString("+")}"
        } shouldBe spans
    }

    @Test
    fun `every error is reported at once`() {
        val result = RichText.parse("<frog>a</bold><gradient:red>")
        result.shouldBeInstanceOf<ParseResult.Invalid>().errors.map { it.offset } shouldBe listOf(0, 7, 14)
        shouldThrow<IllegalArgumentException> { ParseResult.Invalid(emptyList()) }
    }

    @Test
    fun `placeholders are listed and filled in as literal text`() {
        val text = RichText.parseValid("<red>{player}</red> joined {server}")

        text.placeholders() shouldBe setOf("player", "server")
        val filled = text.fill("player" to "<bold>Eve", "server" to "lobby-1")
        filled.toPlain() shouldBe "<bold>Eve joined lobby-1"
        filled.spans().none { Decoration.BOLD in it.decorations } shouldBe true
    }

    @Test
    fun `inserted texts keep their own formatting, and texts join without leaking tags`() {
        val prefix = RichText.parseValid("<gold>[VIP] ")
        val joined =
            RichText.parseValid("{prefix}Eve").insert(mapOf("prefix" to prefix)) + RichText.parseValid("<red>!")
        joined.spans() shouldBe
            listOf(
                Span("[VIP] ", NamedColor.GOLD, emptySet()),
                Span("Eve", null, emptySet()),
                Span("!", NamedColor.RED, emptySet()),
            )
    }

    @Test
    fun `an unfilled placeholder shows as written`() {
        RichText.parseValid("hi {name}").toPlain() shouldBe "hi {name}"
    }
}
