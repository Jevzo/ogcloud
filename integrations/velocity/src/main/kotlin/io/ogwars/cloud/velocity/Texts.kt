package io.ogwars.cloud.velocity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** node-core's `&`-coded texts as Adventure components. */
internal fun legacyText(text: String): Component = LegacyComponentSerializer.legacyAmpersand().deserialize(text)
