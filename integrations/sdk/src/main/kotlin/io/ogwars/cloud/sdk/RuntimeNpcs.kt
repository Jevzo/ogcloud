package io.ogwars.cloud.sdk

import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

/** NPCs created by code; they are not stored and vanish when the server stops. Use them on the main thread. */
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

        fun title(title: String): Builder

        fun subtitle(subtitle: String): Builder

        /** A signed skin texture, as Mojang's session servers return it. */
        fun skin(
            texture: String,
            signature: String,
        ): Builder

        fun lookAtPlayers(lookAtPlayers: Boolean): Builder

        fun onClick(handler: Consumer<NpcClick>): Builder

        fun spawn(): RuntimeNpc
    }
}

interface NpcClick {
    val player: UUID
    val rightClick: Boolean
}
