package io.ogwars.cloud.npc

import io.ogwars.cloud.contracts.PermissionNodes
import io.ogwars.cloud.contracts.node.v1.Location
import io.ogwars.cloud.contracts.node.v1.NpcAction
import io.ogwars.cloud.contracts.node.v1.NpcChangeRequest
import io.ogwars.cloud.contracts.node.v1.NpcTransfer
import io.ogwars.cloud.node.core.command.Argument
import io.ogwars.cloud.node.core.command.Invocation
import io.ogwars.cloud.node.core.command.Subcommand
import io.ogwars.cloud.node.core.command.say
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import java.util.Locale
import java.util.UUID
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy as ProtoRoutingStrategy
import io.ogwars.cloud.contracts.node.v1.Npc as ManagedNpc

/** Where players stand and how they move, from the platform. */
interface NpcPlaces {
    fun location(player: UUID): NpcLocation?

    fun teleport(
        player: UUID,
        location: NpcLocation,
    )
}

/**
 * `/ogcloud npc ...` on a backend (D-0129, v1's NPC command): changes go to the API through the gateway with the
 * player as actor and arrive back as the usual NPC update. NPCs are named by their id or a unique start of it.
 */
class NpcCommands(
    private val hub: NpcHub,
    private val messages: Messages,
    private val groupId: String,
    private val places: NpcPlaces,
    private val send: (NpcChangeRequest.Builder) -> Boolean,
) {
    private val npc = Argument("npc", suggestions = { hub.managed().map { short(it.npcId) } })

    val subcommands: List<Subcommand> =
        listOf(
            read(listOf("list"), emptyList()) { list(it) },
            read(listOf("info"), listOf(Argument("npc", optional = true, suggestions = npc.suggestions))) { info(it) },
            change(listOf("create"), listOf(Argument("title", Argument.Kind.REST, optional = true))) { create(it) },
            change(listOf("delete"), listOf(npc)) { call ->
                withNpc(call) { send(call, NpcChangeRequest.newBuilder().setDeleteNpcId(it.npcId)) }
            },
            change(listOf("movehere"), listOf(npc)) { call ->
                val here = places.location(call.player) ?: return@change
                update(call) { setLocation(here.toProto()) }
            },
            change(listOf("teleport"), listOf(npc)) { call ->
                withNpc(call) { target ->
                    places.teleport(call.player, target.location.toNpcLocation())
                    call.sender.say(messages, Message.NPC_TELEPORTED, "npc" to short(target.npcId))
                }
            },
            change(listOf("title"), listOf(npc, Argument("text", Argument.Kind.REST))) { call ->
                update(call) { setTitle(text(call)) }
            },
            change(listOf("subtitle"), listOf(npc, Argument("text", Argument.Kind.REST))) { call ->
                update(call) { setSubtitle(text(call)) }
            },
            change(listOf("skin"), listOf(npc, Argument("clear"))) { call ->
                if (!call.word("clear").equals(CLEAR, ignoreCase = true)) return@change usage(call, "skin <npc> $CLEAR")
                update(call) { clearSkinTexture().clearSkinSignature() }
            },
            change(listOf("lookat"), listOf(npc, Argument("enabled", Argument.Kind.BOOLEAN))) { call ->
                update(call) { setLookAtPlayers(call.bool("enabled")) }
            },
            action("leftaction") { action -> setLeftClick(action) },
            action("rightaction") { action -> setRightClick(action) },
        )

    private fun read(
        path: List<String>,
        arguments: List<Argument>,
        run: (Invocation) -> Unit,
    ) = Subcommand(listOf(ROOT) + path, arguments, PermissionNodes.ADMIN_NPC, run = run)

    private fun change(
        path: List<String>,
        arguments: List<Argument>,
        run: (Invocation) -> Unit,
    ) = Subcommand(listOf(ROOT) + path, arguments, PermissionNodes.ADMIN_NPC, playersOnly = true, run = run)

    /** `leftaction|rightaction <npc> none` or `... transfer <group> [strategy]`. */
    private fun action(
        name: String,
        set: ManagedNpc.Builder.(NpcAction) -> ManagedNpc.Builder,
    ) = change(
        listOf(name),
        listOf(npc, Argument("action"), Argument("group", optional = true), Argument("strategy", optional = true)),
    ) { call ->
        val action =
            when (call.word("action").lowercase(Locale.ROOT)) {
                NONE -> NpcAction.getDefaultInstance()
                TRANSFER -> transfer(call) ?: return@change
                else -> return@change call.sender.say(messages, Message.NPC_ACTION_INVALID)
            }
        update(call) { set(action) }
    }

    private fun transfer(call: Invocation): NpcAction? {
        val group = call.optional("group") ?: return null.also { call.sender.say(messages, Message.NPC_ACTION_INVALID) }
        val strategy =
            call.optional("strategy")?.let { name ->
                ProtoRoutingStrategy.entries.firstOrNull {
                    it.name == "ROUTING_STRATEGY_" +
                        name.uppercase(
                            Locale.ROOT,
                        )
                }
                    ?: return null.also { call.sender.say(messages, Message.NPC_STRATEGY_INVALID, "strategy" to name) }
            }
        val transfer = NpcTransfer.newBuilder().setGroupId(group).apply { strategy?.let(::setRoutingStrategy) }
        return NpcAction.newBuilder().setTransfer(transfer).build()
    }

    private fun list(call: Invocation) {
        val npcs = hub.managed()
        if (npcs.isEmpty()) return call.sender.say(messages, Message.NPC_LIST_EMPTY)
        call.sender.say(messages, Message.NPC_LIST_HEADER, "count" to npcs.size)
        npcs.forEach {
            call.sender.say(
                messages,
                Message.NPC_LIST_ENTRY,
                "npc" to short(it.npcId),
                "title" to it.title,
                "world" to it.location.world,
                "x" to round(it.location.x),
                "y" to round(it.location.y),
                "z" to round(it.location.z),
            )
        }
    }

    private fun info(call: Invocation) {
        val target =
            call.optional("npc")?.let { reference ->
                hub.findManaged(reference)
                    ?: return call.sender.say(messages, Message.NPC_NOT_FOUND, "npc" to reference)
            } ?: call.sender.player
                ?.let(places::location)
                ?.let(hub::lookedAt)
                ?: return call.sender.say(messages, Message.NPC_NOT_LOOKING)
        val say = { message: Message, values: Array<Pair<String, Any?>> -> call.sender.say(messages, message, *values) }
        say(Message.NPC_INFO_HEADER, arrayOf("npc" to target.npcId))
        say(Message.NPC_INFO_TITLE, arrayOf("title" to target.title))
        say(Message.NPC_INFO_SUBTITLE, arrayOf("subtitle" to target.subtitle))
        val at = target.location
        say(
            Message.NPC_INFO_LOCATION,
            arrayOf(
                "world" to at.world,
                "x" to round(at.x),
                "y" to round(at.y),
                "z" to round(at.z),
            ),
        )
        val skin = if (target.skinTexture.isEmpty()) Message.NPC_SKIN_DEFAULT else Message.NPC_SKIN_CUSTOM
        say(Message.NPC_INFO_SKIN, arrayOf("skin" to messages.text(skin)))
        say(
            Message.NPC_INFO_LOOK,
            arrayOf("look" to messages.text(if (target.lookAtPlayers) Message.YES else Message.NO)),
        )
        say(Message.NPC_INFO_LEFT, arrayOf("action" to describe(target.leftClick)))
        say(Message.NPC_INFO_RIGHT, arrayOf("action" to describe(target.rightClick)))
    }

    /** A new NPC of this server's group where the player stands; the gateway gives it its id (D-0129). */
    private fun create(call: Invocation) {
        val here = places.location(call.player) ?: return
        val npc =
            ManagedNpc
                .newBuilder()
                .setGroupId(groupId)
                .setLocation(here.toProto())
                .setTitle(call.optional("title").orEmpty())
                .setLookAtPlayers(true)
        send(call, NpcChangeRequest.newBuilder().setCreate(npc))
    }

    private fun update(
        call: Invocation,
        edit: ManagedNpc.Builder.() -> ManagedNpc.Builder,
    ) = withNpc(call) { send(call, NpcChangeRequest.newBuilder().setUpdate(it.toBuilder().edit())) }

    private fun withNpc(
        call: Invocation,
        then: (ManagedNpc) -> Unit,
    ) {
        val reference = call.word("npc")
        val target =
            hub.findManaged(reference) ?: return call.sender.say(messages, Message.NPC_NOT_FOUND, "npc" to reference)
        then(target)
    }

    private fun send(
        call: Invocation,
        change: NpcChangeRequest.Builder,
    ) {
        val sent = send(change.setActingPlayerUuid(call.player.toString()))
        if (sent) {
            call.sender.say(messages, Message.NPC_CHANGE_SENT)
        } else {
            call.sender.say(messages, Message.FAILED, "error" to messages.text(Message.ERROR_NOT_CONNECTED))
        }
    }

    private fun usage(
        call: Invocation,
        usage: String,
    ) = call.sender.say(messages, Message.USAGE, "usage" to "$ROOT $usage")

    /** `off` clears a line, as in v1. */
    private fun text(call: Invocation): String =
        call.word("text").takeUnless { it.equals(OFF, ignoreCase = true) }.orEmpty()

    private fun describe(action: NpcAction): String =
        if (!action.hasTransfer()) {
            messages.text(Message.NONE)
        } else {
            val strategy =
                action.transfer.routingStrategy.name
                    .removePrefix("ROUTING_STRATEGY_")
                    .lowercase(Locale.ROOT)
            messages.text(Message.NPC_ACTION_TRANSFER, "group" to action.transfer.groupId, "strategy" to strategy)
        }

    private fun short(id: String) = id.take(SHORT_ID)

    private fun round(value: Double) = "%.1f".format(Locale.ROOT, value)

    private fun NpcLocation.toProto(): Location =
        Location
            .newBuilder()
            .setWorld(world)
            .setX(x)
            .setY(y)
            .setZ(z)
            .setYaw(yaw)
            .setPitch(pitch)
            .build()

    private fun Location.toNpcLocation() = NpcLocation(world, x, y, z, yaw, pitch)

    private companion object {
        const val ROOT = "npc"
        const val SHORT_ID = 8
        const val CLEAR = "clear"
        const val OFF = "off"
        const val NONE = "none"
        const val TRANSFER = "transfer"
    }
}
