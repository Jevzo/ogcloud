package io.ogwars.cloud.sdk

import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

/** NPCs created by code; they are not stored and vanish when the server stops. Safe to use from any thread. */
interface RuntimeNpcs {
    fun create(id: String): RuntimeNpc.Builder

    fun find(id: String): Optional<RuntimeNpc>
}

interface RuntimeNpc {
    val id: String

    fun despawn()

    interface Builder {
        fun location(
            world: String,
            x: Double,
            y: Double,
            z: Double,
            yaw: Float,
            pitch: Float,
        ): Builder

        /** In OgCloud's tag format, as `<gold>BedWars`; `\<` writes a literal `<`. */
        fun title(title: String): Builder

        /** In OgCloud's tag format, like [title]. */
        fun subtitle(subtitle: String): Builder

        /** A signed skin texture, as Mojang's session servers return it. */
        fun skin(
            texture: String,
            signature: String,
        ): Builder

        fun lookAtPlayers(lookAtPlayers: Boolean): Builder

        /** [handler] runs on the clicking player's thread: the main thread, or the player's region on Folia. */
        fun onClick(handler: Consumer<NpcClick>): Builder

        /** Throws IllegalArgumentException naming each error when the title or subtitle is not a valid tag text. */
        fun spawn(): RuntimeNpc
    }
}

interface NpcClick {
    val player: UUID
    val rightClick: Boolean
}
