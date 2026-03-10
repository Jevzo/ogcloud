package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.message.VelocityMessages
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import java.util.concurrent.CompletableFuture

object PermCommands {
    fun create(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("perm")
            .then(createGroupNode(apiClient))
            .then(createPlayerNode(apiClient))

    private fun createGroupNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("group")
            .then(LiteralArgumentBuilder.literal<CommandSource>("list").executes { ctx -> listGroups(ctx, apiClient) })
            .then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("info")
                    .then(OgCloudCommand.wordArg("id").executes { ctx -> groupInfo(ctx, apiClient) }),
            )

    private fun createPlayerNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("player")
            .then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("get")
                    .then(OgCloudCommand.wordArg("name").executes { ctx -> getPlayerPermission(ctx, apiClient) }),
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("set").then(
                    OgCloudCommand.wordArg("name").then(
                        OgCloudCommand.wordArg("group").then(
                            OgCloudCommand.wordArg("duration").executes { ctx -> setPlayerGroup(ctx, apiClient) },
                        ),
                    ),
                ),
            )

    private fun listGroups(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source

        apiClient
            .listPermissionGroups()
            .thenAccept { groups ->
                if (groups.isEmpty()) {
                    OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Permission.Group.LIST_EMPTY)
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Permission.Group.LIST_HEADER,
                    "count" to groups.size,
                )

                groups.forEach { group ->
                    val defaultMarker =
                        if (group.default) {
                            VelocityMessages.Command.Permission.Group.LIST_DEFAULT_MARKER
                        } else {
                            ""
                        }
                    OgCloudCommand.sendMessage(
                        source,
                        OgCloudCommand.format(
                            VelocityMessages.Command.Permission.Group.LIST_ENTRY,
                            "group_id" to group.id,
                            "group_name" to group.name,
                            "weight" to group.weight,
                            "default_marker" to defaultMarker,
                            "permission_count" to group.permissions.size,
                        ),
                    )
                }
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }

    private fun groupInfo(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)

        apiClient
            .getPermissionGroup(groupId)
            .thenAccept { group ->
                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Permission.Group.INFO_HEADER,
                    "group_id" to group.id,
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Group.INFO_NAME,
                        "group_name" to group.name,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Group.INFO_WEIGHT,
                        "weight" to group.weight,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Group.INFO_DEFAULT,
                        "is_default" to group.default,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Group.INFO_PREFIX,
                        "chat_prefix" to group.display.chatPrefix,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Group.INFO_SUFFIX,
                        "chat_suffix" to group.display.chatSuffix,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Group.INFO_PERMISSIONS_HEADER,
                        "count" to group.permissions.size,
                    ),
                )
                group.permissions.forEach { permission ->
                    OgCloudCommand.sendMessage(
                        source,
                        OgCloudCommand.format(
                            VelocityMessages.Command.Permission.Group.INFO_PERMISSION_ENTRY,
                            "permission" to permission,
                        ),
                    )
                }
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }

    private fun getPlayerPermission(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source
        val playerName = ctx.getArgument("name", String::class.java)

        resolveOnlinePlayerUuid(apiClient, source, playerName)
            .thenCompose { uuid ->
                uuid?.let(apiClient::getPlayer) ?: CompletableFuture.completedFuture(null)
            }.thenAccept { player ->
                if (player == null) {
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Permission.Player.INFO_HEADER,
                    "player_name" to player.name,
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Player.INFO_GROUP,
                        "group" to player.permission.group,
                    ),
                )

                val duration =
                    if (player.permission.length == PERMANENT_LENGTH) {
                        VelocityMessages.Command.Permission.Player.DURATION_PERMANENT
                    } else {
                        "${player.permission.length}ms"
                    }

                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Permission.Player.INFO_DURATION,
                        "duration" to duration,
                    ),
                )
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }

    private fun setPlayerGroup(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source
        val playerName = ctx.getArgument("name", String::class.java)
        val group = ctx.getArgument("group", String::class.java)
        val duration = ctx.getArgument("duration", String::class.java)

        resolveOnlinePlayerUuid(apiClient, source, playerName)
            .thenCompose { uuid ->
                uuid?.let { apiClient.setPlayerGroup(it, group, duration) } ?: CompletableFuture.completedFuture(null)
            }.thenAccept { player ->
                if (player == null) {
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Permission.Player.SET_SUCCESS,
                    "player_name" to player.name,
                    "group" to player.permission.group,
                )
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }

    private fun resolveOnlinePlayerUuid(
        apiClient: ApiClient,
        source: CommandSource,
        playerName: String,
    ): CompletableFuture<String?> {
        return apiClient.listOnlinePlayers(name = playerName).thenApply { players ->
            val player = players.firstOrNull()

            if (player == null) {
                OgCloudCommand.sendErrorTemplate(
                    source,
                    VelocityMessages.Command.Permission.Player.NOT_FOUND_ONLINE,
                    "player_name" to playerName,
                )
                return@thenApply null
            }

            player.uuid
        }
    }

    private const val PERMANENT_LENGTH = -1L
}
