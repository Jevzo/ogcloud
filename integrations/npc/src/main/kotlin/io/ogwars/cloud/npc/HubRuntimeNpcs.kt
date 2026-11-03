package io.ogwars.cloud.npc

import io.ogwars.cloud.sdk.NpcClick
import io.ogwars.cloud.sdk.RuntimeNpc
import io.ogwars.cloud.sdk.RuntimeNpcs
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/** The SDK's runtime NPCs (D-0131): created by plugin code, shown by the hub, gone when the server stops. */
class HubRuntimeNpcs(
    private val hub: NpcHub,
) : RuntimeNpcs {
    private val npcs = ConcurrentHashMap<String, RuntimeNpc>()

    override fun create(id: String): RuntimeNpc.Builder = Builder(id)

    override fun find(id: String): Optional<RuntimeNpc> = Optional.ofNullable(npcs[id])

    private inner class Builder(
        private val id: String,
    ) : RuntimeNpc.Builder {
        private var location: NpcLocation? = null
        private var title = ""
        private var subtitle = ""
        private var skin: NpcSkin? = null
        private var lookAtPlayers = true
        private var onClick: Consumer<NpcClick> = Consumer {}

        override fun location(
            world: String,
            x: Double,
            y: Double,
            z: Double,
            yaw: Float,
            pitch: Float,
        ) = apply { location = NpcLocation(world, x, y, z, yaw, pitch) }

        override fun title(title: String) = apply { this.title = title }

        override fun subtitle(subtitle: String) = apply { this.subtitle = subtitle }

        override fun skin(
            texture: String,
            signature: String,
        ) = apply { skin = NpcSkin(texture, signature) }

        override fun lookAtPlayers(lookAtPlayers: Boolean) = apply { this.lookAtPlayers = lookAtPlayers }

        override fun onClick(handler: Consumer<NpcClick>) = apply { onClick = handler }

        override fun spawn(): RuntimeNpc {
            val at = checkNotNull(location) { "Runtime NPC $id needs a location before it spawns" }
            val handler = onClick
            hub.put(
                NpcSpec(
                    NpcHub.RUNTIME + id,
                    at,
                    title,
                    subtitle,
                    skin,
                    lookAtPlayers,
                    { player, right -> handler.accept(Click(player, right)) },
                ),
            )
            return Handle(id).also { npcs[id] = it }
        }
    }

    private inner class Handle(
        override val id: String,
    ) : RuntimeNpc {
        override fun despawn() {
            npcs.remove(id, this)
            hub.remove(NpcHub.RUNTIME + id)
        }
    }

    private data class Click(
        override val player: UUID,
        override val rightClick: Boolean,
    ) : NpcClick
}
