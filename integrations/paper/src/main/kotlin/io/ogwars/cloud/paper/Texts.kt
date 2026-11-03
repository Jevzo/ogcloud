package io.ogwars.cloud.paper

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** node-core's `&`-coded texts as Adventure components. */
internal fun legacyText(text: String): Component = LegacyComponentSerializer.legacyAmpersand().deserialize(text)

/** The named colour of a `&` code, e.g. 'c' is red; null for formats and unknown codes. */
internal fun legacyColor(code: Char?): NamedTextColor? =
    code?.let { LegacyComponentSerializer.parseChar(it)?.color() as? NamedTextColor }
