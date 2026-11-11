package io.ogwars.cloud.text

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RenderTest {
    private fun colors(source: String): List<Int?> =
        RichText.parseValid(source).spans().flatMap { run ->
            List(run.text.codePointCount(0, run.text.length)) { run.color?.rgb }
        }

    @Test
    fun `a gradient runs from its first stop to its last over every code point`() {
        colors("<gradient:#000000:#ffffff>abc</gradient>") shouldBe listOf(0x000000, 0x808080, 0xffffff)
        colors("<gradient:#ff0000:#00ff00:#0000ff>abcde</gradient>") shouldBe
            listOf(0xff0000, 0x808000, 0x00ff00, 0x008080, 0x0000ff)
        colors("<gradient:#000000:#ffffff>x</gradient>") shouldBe listOf(0x000000)
    }

    @Test
    fun `a surrogate pair is one character of a gradient`() {
        colors("<gradient:#000000:#ffffff>a😀b</gradient>") shouldBe listOf(0x000000, 0x808080, 0xffffff)
    }

    @Test
    fun `a gradient spans the text a placeholder was filled with`() {
        val text = RichText.parseValid("<gradient:#000000:#ffffff>{name}</gradient>").fill("name" to "abc")
        text.spans().map { it.color?.rgb } shouldBe listOf(0x000000, 0x808080, 0xffffff)
    }

    @Test
    fun `a colour inside a gradient overrides it, and the gradient spans the rest`() {
        colors("<gradient:#000000:#ffffff>a<red>b</red>c</gradient>") shouldBe listOf(0x000000, 0xFF5555, 0xffffff)
    }

    @Test
    fun `a rainbow goes once round the colour wheel`() {
        colors("<rainbow>abcdef</rainbow>") shouldBe listOf(0xff0000, 0xffff00, 0x00ff00, 0x00ffff, 0x0000ff, 0xff00ff)
    }

    @Test
    fun `equal neighbours merge into one run`() {
        RichText.parseValid("<red>a<bold>b</bold></red>c").spans() shouldBe
            listOf(
                Span("a", NamedColor.RED, emptySet()),
                Span("b", NamedColor.RED, setOf(Decoration.BOLD)),
                Span("c", null, emptySet()),
            )
    }

    @Test
    fun `components keep named colours named and set only the decorations in effect`() {
        RichText.parseValid("<red>a<bold>b</bold></red><#123456>c").toComponent() shouldBe
            Component
                .text()
                .append(Component.text("a", NamedTextColor.RED))
                .append(Component.text("b", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("c", TextColor.color(0x123456)))
                .build()
        RichText.plain("").toComponent() shouldBe Component.empty()
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '`',
        textBlock = """
        <red>Hi                     | §cHi
        <bold>a<red>b               | §la§c§lb
        <red>a</red>b               | §ca§rb
        <red><bold>a</bold>b        | §c§la§cb
        <bold>a</bold><italic>b     | §la§r§ob
        <#ff5555>exact red          | §cexact red
        <#010101>near black         | §0near black
        plain                       | plain""",
    )
    fun `legacy text writes nearest colours and repeats decorations after a colour`(
        source: String,
        legacy: String,
    ) {
        RichText.parseValid(source).toLegacy() shouldBe legacy
    }

    @Test
    fun `gradients collapse to their first colour where 1_8 caps the length`() {
        val text = RichText.parseValid("<gradient:#ff5555:#5555ff>abc</gradient> <rainbow>de")
        text.toLegacy(collapseGradients = true) shouldBe "§cabc§r §4de"
        text.toLegacy() shouldBe "§ca§db§9c§r §4d§3e"
    }

    @Test
    fun `a cut never leaves codes without their text`() {
        val text = RichText.parseValid("<red>[Admin]<bold>12345")
        text.toLegacy(maxLength = 9) shouldBe "§c[Admin]"
        text.toLegacy(maxLength = 12) shouldBe "§c[Admin]§l1"
        text.toLegacy(maxLength = 10) shouldBe "§c[Admin]"
    }

    @Test
    fun `a cut through a surrogate pair leaves no codes behind`() {
        RichText.parseValid("<red>ab<blue>😀x").toLegacy(maxLength = 7) shouldBe "§cab"
    }

    @Test
    fun `a 1_8 team prefix keeps the name's colour by cutting the prefix first`() {
        val prefix = RichText.parseValid("<gradient:#ff5555:#5555ff>[Administrator]</gradient> ")
        legacyTeamPrefix(prefix, NamedColor.GOLD) shouldBe "§c[Administrat§6"
        legacyTeamPrefix(prefix, null) shouldBe "§c[Administrator"
        legacyTeamPrefix(RichText.plain(""), Color.Hex(0x55FF55)) shouldBe "§a"
    }

    @Test
    fun `legacy spans show what legacy text writes, neighbours of one nearest colour merged`() {
        RichText.parseValid("<#ff5555>a<#ff5050>b<bold>c").legacySpans() shouldBe
            listOf(Span("ab", NamedColor.RED, emptySet()), Span("c", NamedColor.RED, setOf(Decoration.BOLD)))
        RichText.parseValid("<red>abcdef").legacySpans(maxLength = 4) shouldBe
            listOf(Span("ab", NamedColor.RED, emptySet()))
    }

    @Test
    fun `gradients count every character they colour, and inner colours take theirs out`() {
        colors("<gradient:#000000:#ffffff>a<bold>b</bold>c</gradient>") shouldBe listOf(0x000000, 0x808080, 0xffffff)
        colors("<gradient:#000000:#ffffff>a<gradient:#ff0000:#0000ff>bc</gradient>d</gradient>") shouldBe
            listOf(0x000000, 0xff0000, 0x0000ff, 0xffffff)
        colors("<gradient:#000000:#ffffff>a<rainbow>b</rainbow>c</gradient>") shouldBe
            listOf(0x000000, 0xff0000, 0xffffff)
        // An unfilled placeholder is shown as written, six characters here.
        colors("<gradient:#000000:#ffffff>{name}</gradient>").size shouldBe 6
    }

    @Test
    fun `the end colour is the one in effect after the last character`() {
        val text =
            RichText.parseValid("<gold>[VIP]</gold> <gradient:#000000:#ffffff>{name}").fill("name" to "ab")
        text.endColor() shouldBe Color.Hex(0xffffff)
        RichText.plain("x").endColor() shouldBe null
        RichText.parseValid("<red>a").endColor() shouldBe NamedColor.RED
        RichText.parseValid("<red>a</red>").endColor() shouldBe null
        RichText.parseValid("<gray>: <reset>").endColor() shouldBe null
        RichText.parseValid("<red>a<bold>b</bold>").endColor() shouldBe NamedColor.RED
        RichText.parseValid("<rainbow><bold></bold></rainbow>").endColor() shouldBe null
    }

    @Test
    fun `an empty colour tag at the end sets the colour that follows, and nowhere else leaves a trace`() {
        val suffix = RichText.parseValid("<gray>: <white>")
        suffix.endColor() shouldBe NamedColor.WHITE
        // The text writes nothing for it; what follows is coloured by its end colour.
        suffix.toLegacy() shouldBe "§7: "
        RichText.parseValid("<red></red>x").spans() shouldBe listOf(Span("x", null, emptySet()))
        RichText
            .parseValid("<gray>: <white>")
            .spans()
            .last()
            .text shouldBe ": "
    }

    @Test
    fun `a placeholder's colour is the one its first character is shown in`() {
        RichText.parseValid("<dark_gray>[<red>{name}<dark_gray>]").colorOf(Placeholders.NAME) shouldBe NamedColor.RED
        RichText.parseValid("[<red>{name}]").colorOf(Placeholders.NAME) shouldBe NamedColor.RED
        RichText.parseValid("<gradient:#000000:#ffffff>{name}").colorOf(Placeholders.NAME) shouldBe Color.Hex(0)
        RichText.parseValid("{name}").colorOf(Placeholders.NAME) shouldBe null
        RichText.parseValid("<red>x").colorOf(Placeholders.NAME) shouldBe null
    }

    @Test
    fun `a section sign in a literal or a value adds no legacy code`() {
        RichText.parseValid("<red>{v}").fill("v" to "§kX").toLegacy() shouldBe "§ckX"
        RichText.parseValid("a§cb").toPlain() shouldBe "acb"
    }

    @Test
    fun `a hex colour's nearest named colour comes from Adventure`() {
        Color.Hex(0xFF5555).nearestNamed() shouldBe NamedTextColor.RED
        Color.Hex(0x010101).nearestNamed() shouldBe NamedTextColor.BLACK
        Color.Hex(0x55FF55).legacyCode() shouldBe "§a"
        NamedColor.DARK_AQUA.nearestLegacy() shouldBe NamedColor.DARK_AQUA
    }

    @Test
    fun `the named colours are Adventure's, by name, value and legacy code`() {
        NamedColor.entries.forEach {
            NamedTextColor.NAMES.value(it.tag)?.value() shouldBe it.rgb
            it.toTextColor() shouldBe NamedTextColor.NAMES.value(it.tag)
        }
        NamedColor.entries.map { it.code }.joinToString("") shouldBe "0123456789abcdef"
    }

    @Test
    fun `hex colours stay within RGB`() {
        shouldThrow<IllegalArgumentException> { Color.Hex(-1) }
        shouldThrow<IllegalArgumentException> { Color.Hex(0x1000000) }
        Color.Hex(0x0a0b0c).hex shouldBe "#0a0b0c"
    }
}
