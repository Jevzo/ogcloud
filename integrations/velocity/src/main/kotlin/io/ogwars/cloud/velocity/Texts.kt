package io.ogwars.cloud.velocity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** node-core's `&`-coded texts as Adventure components. */
internal object Texts {
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    fun of(text: String): Component = legacy.deserialize(text)
}
