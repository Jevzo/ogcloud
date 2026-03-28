package io.ogwars.cloud.paper.command

import io.ogwars.cloud.common.model.NpcClickActionType
import io.ogwars.cloud.common.model.NpcDefinition
import io.ogwars.cloud.common.model.NpcLookAtConfig
import io.ogwars.cloud.common.model.NpcModel
import io.ogwars.cloud.common.model.NpcTransferStrategy
import io.ogwars.cloud.paper.api.ApiClient
import io.ogwars.cloud.paper.api.ApiCreateNpcRequest
import io.ogwars.cloud.paper.api.ApiNpcActionRequest
import io.ogwars.cloud.paper.api.ApiNpcResponse
import io.ogwars.cloud.paper.api.ApiNpcTransferActionRequest
import io.ogwars.cloud.paper.api.ApiUpdateNpcRequest
import io.ogwars.cloud.paper.api.toDefinition
import io.ogwars.cloud.paper.message.PaperMessages
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
            sendErrorTemplate(sender, PaperMessages.Command.Npc.MISSING_PERMISSION, "permission" to PERMISSION)
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
                    "skin" -> mutableListOf(PaperMessages.Common.CLEAR)
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
            failureMessage = PaperMessages.Command.Npc.LIST_FAILURE,
            block = { apiClient.listNpcs(group).join() },
            onSuccess = onSuccess@{ npcs: List<ApiNpcResponse> ->
                if (npcs.isEmpty()) {
                    sendPrefixed(sender, PaperMessages.Command.Npc.LIST_EMPTY)
                    return@onSuccess
                }

                sendPrefixedTemplate(sender, PaperMessages.Command.Npc.LIST_HEADER, "count" to npcs.size)
                npcs.sortedWith(compareBy<ApiNpcResponse> { it.group }.thenBy { it.id }).forEach { npc ->
                    sendRawTemplate(
                        sender,
                        PaperMessages.Command.Npc.LIST_ENTRY,
                        "npc_id" to npc.id,
                        "group" to npc.group,
                        "world" to npc.location.world,
                        "x" to format(npc.location.x),
                        "y" to format(npc.location.y),
                        "z" to format(npc.location.z),
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
                sendError(sender, PaperMessages.Command.Npc.INFO_LOOK_TARGET_MISSING)
                return true
            }

            sendNpcInfo(sender, npc)
            return true
        }

        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        runAsync(
            sender = sender,
            failureMessage = PaperMessages.Command.Npc.INFO_FAILURE,
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
            failureMessage = PaperMessages.Command.Npc.CREATE_FAILURE,
            block = { apiClient.createNpc(request).join() },
            onSuccess = { npc: ApiNpcResponse ->
                applyLocalNpc(npc)
                sendPrefixedTemplate(
                    sender,
                    PaperMessages.Command.Npc.CREATE_SUCCESS,
                    "npc_id" to npc.id,
                    "group" to npc.group,
                )
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
            failureMessage = PaperMessages.Command.Npc.DELETE_FAILURE,
            block = {
                apiClient.deleteNpc(id).join()
                id
            },
            onSuccess = { deletedId: String ->
                if (npcManager.getLocalNpc(deletedId) != null) {
                    npcManager.removeManagedNpc(deletedId)
                }
                sendPrefixedTemplate(sender, PaperMessages.Command.Npc.DELETE_SUCCESS, "npc_id" to deletedId)
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
            successMessage = formatMessage(PaperMessages.Command.Npc.MOVE_HERE_SUCCESS, "npc_id" to id),
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
            sendErrorTemplate(sender, PaperMessages.Command.Npc.TELEPORT_NOT_LOADED, "npc_id" to id)
            return true
        }

        val location = resolveLocation(npc)
        if (location == null) {
            sendErrorTemplate(
                sender,
                PaperMessages.Command.Npc.TELEPORT_WORLD_NOT_LOADED,
                "world" to npc.location.world,
            )
            return true
        }

        player.teleport(location)
        sendPrefixedTemplate(sender, PaperMessages.Command.Npc.TELEPORT_SUCCESS, "npc_id" to id)
        return true
    }

    private fun handleTitle(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val parsed = parseTextValue(sender, args, 3, "title") ?: return true
        updateNpc(
            sender = sender,
            id = id,
            request = ApiUpdateNpcRequest(title = parsed.value),
            successMessage = formatMessage(PaperMessages.Command.Npc.TITLE_SUCCESS, "npc_id" to id),
        )
        return true
    }

    private fun handleSubtitle(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        val parsed = parseTextValue(sender, args, 3, "subtitle") ?: return true
        updateNpc(
            sender = sender,
            id = id,
            request = ApiUpdateNpcRequest(subtitle = parsed.value),
            successMessage = formatMessage(PaperMessages.Command.Npc.SUBTITLE_SUCCESS, "npc_id" to id),
        )
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
                    sendError(sender, PaperMessages.Command.Npc.MODEL_INVALID)
                    return true
                }

        updateNpc(
            sender = sender,
            id = id,
            request = ApiUpdateNpcRequest(model = model),
            successMessage = formatMessage(PaperMessages.Command.Npc.MODEL_SUCCESS, "npc_id" to id),
        )
        return true
    }

    private fun handleSkin(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val id = args.getOrNull(2) ?: return sendUsageAndStop(sender)
        return if (args.size == 4 && args[3].equals(PaperMessages.Common.CLEAR, ignoreCase = true)) {
            updateNpc(
                sender = sender,
                id = id,
                request = ApiUpdateNpcRequest(clearSkin = true),
                successMessage = formatMessage(PaperMessages.Command.Npc.SKIN_CLEAR_SUCCESS, "npc_id" to id),
            )
            true
        } else {
            sendPrefixed(sender, PaperMessages.Command.Npc.SKIN_USAGE)
            true
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
                    sendError(sender, PaperMessages.Command.Npc.LOOK_AT_INVALID)
                    return true
                }
        val radius = args.getOrNull(4)?.toDoubleOrNull() ?: DEFAULT_LOOK_AT_RADIUS

        updateNpc(
            sender = sender,
            id = id,
            request = ApiUpdateNpcRequest(lookAt = NpcLookAtConfig(enabled, radius)),
            successMessage = formatMessage(PaperMessages.Command.Npc.LOOK_AT_SUCCESS, "npc_id" to id),
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
        updateNpc(
            sender = sender,
            id = id,
            request = request,
            successMessage =
                formatMessage(
                    PaperMessages.Command.Npc.ACTION_SUCCESS,
                    "side" to side,
                    "npc_id" to id,
                ),
        )
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
            failureMessage = PaperMessages.Command.Npc.UPDATE_FAILURE,
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
        sendPrefixedTemplate(sender, PaperMessages.Command.Npc.INFO_HEADER, "npc_id" to npc.id)
        sendRawTemplate(sender, PaperMessages.Command.Npc.INFO_GROUP, "group" to npc.group)
        sendRawTemplate(
            sender,
            PaperMessages.Command.Npc.INFO_LOCATION,
            "world" to npc.location.world,
            "x" to format(npc.location.x),
            "y" to format(npc.location.y),
            "z" to format(npc.location.z),
            "yaw" to format(npc.location.yaw.toDouble()),
            "pitch" to format(npc.location.pitch.toDouble()),
        )
        sendRawTemplate(
            sender,
            PaperMessages.Command.Npc.INFO_TITLE,
            "title" to (npc.title ?: PaperMessages.Common.OFF),
        )
        sendRawTemplate(
            sender,
            PaperMessages.Command.Npc.INFO_SUBTITLE,
            "subtitle" to (npc.subtitle ?: PaperMessages.Common.OFF),
        )
        sendRawTemplate(
            sender,
            PaperMessages.Command.Npc.INFO_MODEL,
            "model" to npc.model.name.lowercase(Locale.ROOT),
        )

        val skin = npc.skin
        val skinState =
            when {
                skin == null -> PaperMessages.Common.CLEAR
                skin.textureSignature == null -> PaperMessages.Command.Npc.INFO_SKIN_UNSIGNED
                else -> PaperMessages.Command.Npc.INFO_SKIN_SIGNED
            }
        sendRawTemplate(sender, PaperMessages.Command.Npc.INFO_SKIN, "skin" to skinState)
        sendRawTemplate(
            sender,
            PaperMessages.Command.Npc.INFO_LOOK_AT,
            "enabled" to npc.lookAt.enabled,
            "radius" to format(npc.lookAt.radius),
        )
        sendRawTemplate(sender, PaperMessages.Command.Npc.INFO_LEFT, "action" to formatAction(npc, left = true))
        sendRawTemplate(sender, PaperMessages.Command.Npc.INFO_RIGHT, "action" to formatAction(npc, left = false))
    }

    private fun formatAction(
        npc: NpcDefinition,
        left: Boolean,
    ): String {
        val action = if (left) npc.leftAction else npc.rightAction
        if (action.type == NpcClickActionType.NONE || action.targetGroup.isNullOrBlank()) {
            return PaperMessages.Common.NONE
        }

        val strategy =
            action.routingStrategy
                ?.name
                ?.lowercase(Locale.ROOT)
                ?.replace('_', '-') ?: PaperMessages.Common.NETWORK_DEFAULT
        return formatMessage(
            PaperMessages.Command.Npc.INFO_ACTION_TRANSFER,
            "group" to action.targetGroup,
            "strategy" to strategy,
        )
    }

    private fun parseTextValue(
        sender: CommandSender,
        args: Array<out String>,
        startIndex: Int,
        fieldName: String,
    ): ParsedTextValue? {
        val parts = args.drop(startIndex)
        if (parts.isEmpty()) {
            sendErrorTemplate(sender, PaperMessages.Command.Npc.TEXT_VALUE_REQUIRED, "field" to fieldName)
            return null
        }

        val raw = parts.joinToString(" ").trim()
        if (raw.isEmpty()) {
            sendErrorTemplate(sender, PaperMessages.Command.Npc.TEXT_VALUE_REQUIRED, "field" to fieldName)
            return null
        }

        return ParsedTextValue(
            value =
                if (raw.equals(PaperMessages.Common.OFF, ignoreCase = true)) {
                    null
                } else {
                    raw
                },
        )
    }

    private fun parseStrategy(
        sender: CommandSender,
        raw: String,
    ): NpcTransferStrategy? =
        runCatching { NpcTransferStrategy.valueOf(raw.replace('-', '_').uppercase(Locale.ROOT)) }
            .getOrElse {
                sendErrorTemplate(sender, PaperMessages.Command.Npc.ROUTING_STRATEGY_INVALID, "strategy" to raw)
                null
            }

    private fun resolveLocation(npc: NpcDefinition): Location? =
        Bukkit.getWorld(npc.location.world)?.let(npc.location::toBukkit)

    private fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sendError(sender, PaperMessages.Command.Npc.PLAYER_ONLY)
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
                            Runnable {
                                sendErrorTemplate(
                                    sender,
                                    PaperMessages.Command.Npc.FAILURE_TEMPLATE,
                                    "message" to failureMessage,
                                    "error" to (exception.message ?: PaperMessages.Common.UNKNOWN_ERROR),
                                )
                            },
                        )
                    }
            },
        )
    }

    private fun sendUsage(sender: CommandSender) {
        sendPrefixed(sender, PaperMessages.Command.Npc.ROOT_USAGE)
    }

    private fun sendNpcUsage(sender: CommandSender) {
        sendPrefixed(sender, PaperMessages.Command.Npc.SUBCOMMAND_USAGE)
    }

    private fun sendPrefixed(
        sender: CommandSender,
        message: String,
    ) {
        sendRaw(sender, "${PaperMessages.Prefix.COMMAND}&7$message")
    }

    private fun sendError(
        sender: CommandSender,
        message: String,
    ) {
        sendRaw(sender, "${PaperMessages.Prefix.COMMAND}&c$message")
    }

    private fun sendPrefixedTemplate(
        sender: CommandSender,
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ) {
        sendPrefixed(sender, formatMessage(template, *placeholders))
    }

    private fun sendErrorTemplate(
        sender: CommandSender,
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ) {
        sendError(sender, formatMessage(template, *placeholders))
    }

    private fun sendRawTemplate(
        sender: CommandSender,
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ) {
        sendRaw(sender, formatMessage(template, *placeholders))
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

    private fun formatMessage(
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ): String = PaperMessages.format(template, *placeholders)

    private fun format(value: Double): String = FORMAT.format(value)

    companion object {
        private const val PERMISSION = "ogcloud.admin.npc"
        private const val DEFAULT_LOOK_AT_RADIUS = 4.0
        private val FORMAT = DecimalFormat("0.##")
    }

    private data class ParsedTextValue(
        val value: String?,
    )
}
