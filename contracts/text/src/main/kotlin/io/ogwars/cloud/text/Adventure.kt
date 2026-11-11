package io.ogwars.cloud.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * The text as an Adventure component for 1.16 and newer clients; Velocity and ViaBackwards on the proxy downsample it
 * for older ones (D-0204).
 */
fun RichText.toComponent(): Component {
    val spans = spans()
    if (spans.isEmpty()) return Component.empty()
    return Component.text().append(spans.map { Component.text(it.text, style(it)) }).build()
}

/** As an Adventure colour: a named colour stays named, so a legacy serializer keeps its exact code. */
fun Color.toTextColor(): TextColor =
    when (this) {
        is NamedColor -> checkNotNull(NamedTextColor.NAMES.value(tag)) { "Adventure has no colour $tag" }
        is Color.Hex -> TextColor.color(rgb)
    }

/** The nearest named colour, which older clients and team colours need (D-0204). */
fun Color.nearestNamed(): NamedTextColor = NamedTextColor.nearestTo(toTextColor())

private fun style(span: Span): Style {
    val builder = Style.style()
    span.color?.let { builder.color(it.toTextColor()) }
    span.decorations.forEach { builder.decoration(it.toTextDecoration(), true) }
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
