package io.ogwars.cloud.paper.command

import io.ogwars.cloud.common.model.NpcClickActionType
import io.ogwars.cloud.common.model.NpcDefinition
import io.ogwars.cloud.common.model.NpcLookAtConfig
import io.ogwars.cloud.common.model.NpcModel
import io.ogwars.cloud.common.model.NpcSkin
import io.ogwars.cloud.common.model.NpcTransferStrategy
import io.ogwars.cloud.paper.api.ApiClient
import io.ogwars.cloud.paper.api.ApiCreateNpcRequest
import io.ogwars.cloud.paper.api.ApiNpcActionRequest
import io.ogwars.cloud.paper.api.ApiNpcResponse
import io.ogwars.cloud.paper.api.ApiNpcTransferActionRequest
import io.ogwars.cloud.paper.api.ApiUpdateNpcRequest
import io.ogwars.cloud.paper.api.toDefinition
import io.ogwars.cloud.paper.npc.NpcManager
import io.ogwars.cloud.paper.npc.npcLocationFromBukkit
import io.ogwars.cloud.paper.npc.toBukkit
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.text.DecimalFormat
import java.util.Locale

class OgCloudNpcCommand(
    private val plugin: JavaPlugin,
    private val apiClient: ApiClient,
    private val npcManager: NpcManager,
    private val groupName: String,
) : TabExecutor {
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (!sender.hasPermission(PERMISSION)) {
            sendError(sender, "Missing permission: $PERMISSION")
            return true
        }

        if (args.isEmpty() || args[0].lowercase(Locale.ROOT) != "npc") {
            sendUsage(sender)
            return true
        }

        if (args.size == 1) {
            sendNpcUsage(sender)
            return true
        }

        return when (args[1].lowercase(Locale.ROOT)) {
            "list" -> handleList(sender, args)
            "info" -> handleInfo(sender, args)
            "create" -> handleCreate(sender, args)
            "delete" -> handleDelete(sender, args)
            "movehere" -> handleMoveHere(sender, args)
            "teleport" -> handleTeleport(sender, args)
            "title" -> handleTitle(sender, args)
            "subtitle" -> handleSubtitle(sender, args)
            "model" -> handleModel(sender, args)
            "skin" -> handleSkin(sender, args)
            "lookat" -> handleLookAt(sender, args)
            "leftaction" -> handleAction(sender, args, left = true)
            "rightaction" -> handleAction(sender, args, left = false)
            else -> {
                sendNpcUsage(sender)
                true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        if (args.isEmpty()) {
            return mutableListOf("npc")
        }

        if (args[0].lowercase(Locale.ROOT) != "npc") {
            return mutableListOf()
        }

        return when (args.size) {
            2 ->
                mutableListOf(
                    "list",
                    "info",
                    "create",
                    "delete",
                    "movehere",
                    "teleport",
                    "title",
                    "subtitle",
                    "model",
                    "skin",
                    "lookat",
                    "leftaction",
                    "rightaction",
                )

            3 ->
                when (args[1].lowercase(Locale.ROOT)) {
                    "info",
                    "delete",
                    "movehere",
                    "teleport",
                    "title",
                    "subtitle",
                    "model",
                    "skin",
                    "lookat",
                    "leftaction",
                    "rightaction",
                    -> npcManager.getLocalNpcs().map(NpcDefinition::id).toMutableList()
                    else -> mutableListOf()
                }

            4 ->
                when (args[1].lowercase(Locale.ROOT)) {
                    "model" -> mutableListOf("steve", "alex")
                    "skin" -> mutableListOf("signed", "unsigned", "clear")
                    "lookat" -> mutableListOf("true", "false")
                    "leftaction",
                    "rightaction",
                    -> mutableListOf("none", "transfer")
                    else -> mutableListOf()
                }

            5 ->
                when (args[1].lowercase(Locale.ROOT)) {
                    "leftaction",
                    "rightaction",
                    -> if (args[3].equals("transfer", ignoreCase = true)) mutableListOf(groupName) else mutableListOf()
                    else -> mutableListOf()
                }

            6 ->
                when (args[1].lowercase(Locale.ROOT)) {
                    "leftaction",
                    "rightaction",
                    -> mutableListOf("most-filled", "least-filled", "balanced")
                    else -> mutableListOf()
                }

            else -> mutableListOf()
        }
    }

    private fun handleList(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val group = args.getOrNull(2)
        runAsync(
            sender = sender,
            failureMessage = "Failed to list NPCs",
            block = { apiClient.listNpcs(group).join() },
            onSuccess = onSuccess@{ npcs: List<ApiNpcResponse> ->
                if (npcs.isEmpty()) {
                    sendPrefixed(sender, "No NPCs found.")
                    return@onSuccess
                }

                sendPrefixed(sender, "NPCs: ${npcs.size}")
                npcs.sortedWith(compareBy<ApiNpcResponse> { it.group }.thenBy { it.id }).forEach { npc ->
                    sendRaw(
                        sender,
                        "&8- &f${npc.id} &7group=&f${npc.group} &7world=&f${npc.location.world} &7x=&f${format(
                            npc.location.x,
                        )} &7y=&f${format(npc.location.y)} &7z=&f${format(npc.location.z)}",
                    )
                }
            },
        )
        return true
    }

    private fun handleInfo(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        if (args.size == 2) {
            val player = requirePlayer(sender) ?: return true
            val npc = npcManager.findLookTarget(player)
            if (npc == null) {
                sendError(sender, "You are not looking at a local NPC.")
                return true
            }

            sendNpcInfo(sender, npc)
            return true
        }

        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        runAsync(
            sender = sender,
            failureMessage = "Failed to fetch NPC info",
            block = { apiClient.getNpc(id).join() },
            onSuccess = { npc: ApiNpcResponse -> sendNpcInfo(sender, npc.toDefinition()) },
        )
        return true
    }

    private fun handleCreate(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        if (args.size != 4) {
            return sendUsageAndStop(sender)
        }

        val player = requirePlayer(sender) ?: return true
        val request =
            ApiCreateNpcRequest(
                id = args[2],
                group = args[3],
                location = npcLocationFromBukkit(player.location),
            )

        runAsync(
            sender = sender,
            failureMessage = "Failed to create NPC",
            block = { apiClient.createNpc(request).join() },
            onSuccess = { npc: ApiNpcResponse ->
                applyLocalNpc(npc)
                sendPrefixed(sender, "Created NPC ${npc.id} for group ${npc.group}.")
            },
        )
        return true
    }

    private fun handleDelete(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        runAsync(
            sender = sender,
            failureMessage = "Failed to delete NPC",
            block = {
                apiClient.deleteNpc(id).join()
                id
            },
            onSuccess = { deletedId: String ->
                if (npcManager.getLocalNpc(deletedId) != null) {
                    npcManager.removeManagedNpc(deletedId)
                }
                sendPrefixed(sender, "Deleted NPC $deletedId.")
            },
        )
        return true
    }

    private fun handleMoveHere(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val player = requirePlayer(sender) ?: return true

        updateNpc(
            sender = sender,
            id = id,
            request = ApiUpdateNpcRequest(location = npcLocationFromBukkit(player.location)),
            successMessage = "Moved NPC $id to your location.",
        )
        return true
    }

    private fun handleTeleport(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val player = requirePlayer(sender) ?: return true
        val npc = npcManager.getLocalNpc(id)

        if (npc == null) {
            sendError(sender, "NPC $id is not loaded on this server.")
            return true
        }

        val location = resolveLocation(npc)
        if (location == null) {
            sendError(sender, "NPC world ${npc.location.world} is not loaded on this server.")
            return true
        }

        player.teleport(location)
        sendPrefixed(sender, "Teleported to NPC $id.")
        return true
    }

    private fun handleTitle(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val parsed = parseTextValue(sender, args, 3, "title") ?: return true
        updateNpc(sender, id, ApiUpdateNpcRequest(title = parsed.value), "Updated title for NPC $id.")
        return true
    }

    private fun handleSubtitle(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val parsed = parseTextValue(sender, args, 3, "subtitle") ?: return true
        updateNpc(sender, id, ApiUpdateNpcRequest(subtitle = parsed.value), "Updated subtitle for NPC $id.")
        return true
    }

    private fun handleModel(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val model =
            runCatching { NpcModel.valueOf(args.getOrNull(3)?.uppercase(Locale.ROOT) ?: "") }
                .getOrElse {
                    sendError(sender, "Model must be steve or alex.")
                    return true
                }

        updateNpc(sender, id, ApiUpdateNpcRequest(model = model), "Updated model for NPC $id.")
        return true
    }

    private fun handleSkin(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        return when (args.getOrNull(3)?.lowercase(Locale.ROOT)) {
            "clear" -> {
                updateNpc(
                    sender,
                    id,
                    ApiUpdateNpcRequest(clearSkin = true),
                    "Reset skin for NPC $id to the default profile.",
                )
                true
            }

            "unsigned" -> {
                val value = args.getOrNull(4) ?: return sendUsageAndStop(sender)
                updateNpc(sender, id, ApiUpdateNpcRequest(skin = NpcSkin(value, null)), "Updated skin for NPC $id.")
                true
            }

            "signed" -> {
                val value = args.getOrNull(4) ?: return sendUsageAndStop(sender)
                val signature = args.getOrNull(5) ?: return sendUsageAndStop(sender)
                updateNpc(
                    sender,
                    id,
                    ApiUpdateNpcRequest(skin = NpcSkin(value, signature)),
                    "Updated skin for NPC $id.",
                )
                true
            }

            else -> sendUsageAndStop(sender)
        }
    }

    private fun handleLookAt(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val enabled =
            args.getOrNull(3)?.toBooleanStrictOrNull()
                ?: run {
                    sendError(sender, "lookat requires true or false.")
                    return true
                }
        val radius =
            args.getOrNull(4)?.toDoubleOrNull()
                ?: DEFAULT_LOOK_AT_RADIUS

        updateNpc(
            sender,
            id,
            ApiUpdateNpcRequest(lookAt = NpcLookAtConfig(enabled, radius)),
            "Updated look-at settings for NPC $id.",
        )
        return true
    }

    private fun handleAction(
        sender: CommandSender,
        args: Array<out String>,
        left: Boolean,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val action =
            when (args.getOrNull(3)?.lowercase(Locale.ROOT)) {
                "none" -> ApiNpcActionRequest(NpcClickActionType.NONE)
                "transfer" -> {
                    val group = args.getOrNull(4) ?: return sendUsageAndStop(sender)
                    val strategy =
                        if (args.size <= 5) {
                            null
                        } else {
                            parseStrategy(sender, args[5]) ?: return true
                        }
                    ApiNpcActionRequest(
                        type = NpcClickActionType.TRANSFER,
                        transfer = ApiNpcTransferActionRequest(group = group, routingStrategy = strategy),
                    )
                }

                else -> return sendUsageAndStop(sender)
            }

        val request =
            if (left) {
                ApiUpdateNpcRequest(leftAction = action)
            } else {
                ApiUpdateNpcRequest(rightAction = action)
            }

        val side = if (left) "left" else "right"
        updateNpc(sender, id, request, "Updated $side click action for NPC $id.")
        return true
    }

    private fun updateNpc(
        sender: CommandSender,
        id: String,
        request: ApiUpdateNpcRequest,
        successMessage: String,
    ) {
        runAsync(
            sender = sender,
            failureMessage = "Failed to update NPC",
            block = { apiClient.updateNpc(id, request).join() },
            onSuccess = { npc: ApiNpcResponse ->
                applyLocalNpc(npc)
                sendPrefixed(sender, successMessage)
            },
        )
    }

    private fun applyLocalNpc(npc: ApiNpcResponse) {
        if (npc.group == groupName) {
            npcManager.upsertManagedNpc(npc.toDefinition())
            return
        }

        npcManager.removeManagedNpc(npc.id)
    }

    private fun sendNpcInfo(
        sender: CommandSender,
        npc: NpcDefinition,
    ) {
        sendPrefixed(sender, "NPC ${npc.id}")
        sendRaw(sender, "&8- &7group: &f${npc.group}")
        sendRaw(
            sender,
            "&8- &7location: &f${npc.location.world} &7(${format(
                npc.location.x,
            )}, ${format(
                npc.location.y,
            )}, ${format(
                npc.location.z,
            )}) &7yaw=&f${format(npc.location.yaw.toDouble())} &7pitch=&f${format(npc.location.pitch.toDouble())}",
        )
        sendRaw(sender, "&8- &7title: &f${npc.title ?: "off"}")
        sendRaw(sender, "&8- &7subtitle: &f${npc.subtitle ?: "off"}")
        sendRaw(sender, "&8- &7model: &f${npc.model.name.lowercase(Locale.ROOT)}")
        val skin = npc.skin
        val skinState =
            when {
                skin == null -> "clear"
                skin.textureSignature == null -> "unsigned"
                else -> "signed"
            }
        sendRaw(sender, "&8- &7skin: &f$skinState")
        sendRaw(sender, "&8- &7look-at: &f${npc.lookAt.enabled} &7radius=&f${format(npc.lookAt.radius)}")
        sendRaw(sender, "&8- &7left: &f${formatAction(npc, left = true)}")
        sendRaw(sender, "&8- &7right: &f${formatAction(npc, left = false)}")
    }

    private fun formatAction(
        npc: NpcDefinition,
        left: Boolean,
    ): String {
        val action = if (left) npc.leftAction else npc.rightAction
        if (action.type == NpcClickActionType.NONE || action.targetGroup.isNullOrBlank()) {
            return "none"
        }

        val strategy =
            action.routingStrategy
                ?.name
                ?.lowercase(Locale.ROOT)
                ?.replace('_', '-') ?: "network-default"
        return "transfer ${action.targetGroup} ($strategy)"
    }

    private fun parseTextValue(
        sender: CommandSender,
        args: Array<out String>,
        startIndex: Int,
        fieldName: String,
    ): ParsedTextValue? {
        val parts = args.drop(startIndex)
        if (parts.isEmpty()) {
            sendError(sender, "$fieldName requires text or off.")
            return null
        }

        val raw = parts.joinToString(" ").trim()
        if (raw.isEmpty()) {
            sendError(sender, "$fieldName requires text or off.")
            return null
        }

        return ParsedTextValue(value = if (raw.equals("off", ignoreCase = true)) null else raw)
    }

    private fun parseStrategy(
        sender: CommandSender,
        raw: String,
    ): NpcTransferStrategy? =
        runCatching { NpcTransferStrategy.valueOf(raw.replace('-', '_').uppercase(Locale.ROOT)) }
            .getOrElse {
                sendError(sender, "Invalid routing strategy: $raw")
                null
            }

    private fun resolveLocation(npc: NpcDefinition): Location? =
        Bukkit.getWorld(npc.location.world)?.let(npc.location::toBukkit)

    private fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sendError(sender, "This command must be executed by a player.")
            return null
        }
        return sender
    }

    private fun <T> runAsync(
        sender: CommandSender,
        failureMessage: String,
        block: () -> T,
        onSuccess: (T) -> Unit,
    ) {
        plugin.server.scheduler.runTaskAsynchronously(
            plugin,
            Runnable {
                runCatching(block)
                    .onSuccess { result ->
                        plugin.server.scheduler.runTask(plugin, Runnable { onSuccess(result) })
                    }.onFailure { exception ->
                        plugin.server.scheduler.runTask(
                            plugin,
                            Runnable { sendError(sender, "$failureMessage: ${exception.message ?: "unknown error"}") },
                        )
                    }
            },
        )
    }

    private fun sendUsage(sender: CommandSender) {
        sendPrefixed(sender, "Use /ogcloud npc ...")
    }

    private fun sendNpcUsage(sender: CommandSender) {
        sendPrefixed(
            sender,
            "Subcommands: list, info, create, delete, movehere, teleport, title, subtitle, model, skin, lookat, leftaction, rightaction",
        )
    }

    private fun sendPrefixed(
        sender: CommandSender,
        message: String,
    ) {
        sendRaw(sender, "$PREFIX&7$message")
    }

    private fun sendError(
        sender: CommandSender,
        message: String,
    ) {
        sendRaw(sender, "$PREFIX&c$message")
    }

    private fun sendRaw(
        sender: CommandSender,
        message: String,
    ) {
        sender.sendMessage(legacySerializer.deserialize(message))
    }

    private fun sendUsageAndStop(sender: CommandSender): Boolean {
        sendNpcUsage(sender)
        return true
    }

    private fun format(value: Double): String = FORMAT.format(value)

    companion object {
        private const val PERMISSION = "ogcloud.admin.npc"
        private const val PREFIX = "&6OgCloud &8| "
        private const val DEFAULT_LOOK_AT_RADIUS = 4.0
        private val FORMAT = DecimalFormat("0.##")
    }

    private data class ParsedTextValue(
        val value: String?,
    )
}
