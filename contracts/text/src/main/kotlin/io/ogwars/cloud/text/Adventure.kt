package io.ogwars.cloud.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/** The text as an Adventure component for 1.16 and newer clients; Velocity and ViaBackwards downsample it for older ones. */
fun RichText.toComponent(): Component {
    val runs = runs()
    if (runs.isEmpty()) return Component.empty()
    return Component.text().append(runs.map { Component.text(it.text, style(it)) }).build()
}

/** The Adventure colour of [color]: a named colour stays named, so a legacy serializer keeps its exact code. */
fun Color.toTextColor(): TextColor =
    when (this) {
        is Color.Named -> checkNotNull(NamedTextColor.NAMES.value(name)) { "Adventure has no colour $name" }
        is Color.Hex -> TextColor.color(rgb)
    }

/** The named colour nearest to [color], as older clients and team colours need one (D-0204). */
fun Color.nearestNamed(): NamedTextColor =
    when (this) {
        is Color.Named -> checkNotNull(NamedTextColor.NAMES.value(name)) { "Adventure has no colour $name" }
        is Color.Hex -> NamedTextColor.nearestTo(TextColor.color(rgb))
    }

private fun style(run: Run): Style {
    val builder = Style.style()
    run.color?.let { builder.color(it.toTextColor()) }
    run.decorations.forEach { builder.decoration(it.toTextDecoration(), true) }
    return builder.build()
}

private fun Decoration.toTextDecoration(): TextDecoration =
    when (this) {
        Decoration.BOLD -> TextDecoration.BOLD
        Decoration.ITALIC -> TextDecoration.ITALIC
        Decoration.UNDERLINED -> TextDecoration.UNDERLINED
        Decoration.STRIKETHROUGH -> TextDecoration.STRIKETHROUGH
        Decoration.OBFUSCATED -> TextDecoration.OBFUSCATED
    }
