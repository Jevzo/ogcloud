package io.ogwars.cloud.text

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
        RichText.parseValid(source).runs().flatMap { run ->
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
        text.runs().map { it.color?.rgb } shouldBe listOf(0x000000, 0x808080, 0xffffff)
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
        RichText.parseValid("<red>a<bold>b</bold></red>c").runs() shouldBe
            listOf(
                Run("a", Color.NAMED.getValue("red"), emptySet()),
                Run("b", Color.NAMED.getValue("red"), setOf(Decoration.BOLD)),
                Run("c", null, emptySet()),
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
    fun `the first and end colours are those of the first and last characters`() {
        val text =
            RichText.parseValid("<gold>[VIP]</gold> <gradient:#000000:#ffffff>{name}</gradient>").fill("name" to "ab")
        text.firstColor() shouldBe Color.NAMED.getValue("gold")
        text.endColor() shouldBe Color.Hex(0xffffff)
        RichText.plain("x").endColor() shouldBe null
    }

    @Test
    fun `an empty colour tag at the end sets the colour that follows, and nowhere else leaves a trace`() {
        val suffix = RichText.parseValid("<gray>: <white>")
        suffix.endColor() shouldBe Color.NAMED.getValue("white")
        suffix.toLegacy() shouldBe "§7: §f"
        RichText.parseValid("<red></red>x").runs() shouldBe listOf(Run("x", null, emptySet()))
    }

    @Test
    fun `a hex colour's nearest named colour comes from Adventure`() {
        Color.Hex(0xFF5555).nearestNamed() shouldBe NamedTextColor.RED
        Color.Hex(0x010101).nearestNamed() shouldBe NamedTextColor.BLACK
        legacyCode(Color.Hex(0x55FF55)) shouldBe 'a'
    }
}
