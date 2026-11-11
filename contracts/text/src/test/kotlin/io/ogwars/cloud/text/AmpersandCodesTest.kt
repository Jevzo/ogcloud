package io.ogwars.cloud.text

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AmpersandCodesTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '`',
        textBlock = """
        &aAn OgCloud network        | <green>An OgCloud network
        &l&cX                       | <bold><reset><red>X
        &cRed &lBold                | <red>Red <bold>Bold
        &7a&rb                      | <gray>a<reset>b
        &rplain                     | plain
        &C upper case               | <red> upper case
        &z and & stay               | &z and & stay
        a<b{c}\d                    | a\<b\{c}\\d""",
    )
    fun `codes become tags and the format's own characters are escaped`(
        legacy: String,
        tags: String,
    ) {
        AmpersandCodes.toTags(legacy) shouldBe tags
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '`',
        textBlock = """
        &aAn OgCloud network        | §aAn OgCloud network
        &cRed &lBold                | §cRed §lBold
        &l&cX                       | §cX
        &8[&bOgCloud&8] &7a <b>     | §8[§bOgCloud§8] §7a <b>""",
    )
    fun `converted texts render as the legacy codes did`(
        legacy: String,
        rendered: String,
    ) {
        RichText.parseValid(AmpersandCodes.toTags(legacy)).toLegacy() shouldBe rendered
    }
}
