package io.ogwars.cloud.velocity.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import io.ogwars.cloud.velocity.api.ApiClient
import java.util.concurrent.CompletableFuture

object PermCommands {

    fun create(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("perm")
            .then(createGroupNode(apiClient))
            .then(createPlayerNode(apiClient))
    }

    private fun createGroupNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("group")
            .then(LiteralArgumentBuilder.literal<CommandSource>("list").executes { ctx -> listGroups(ctx, apiClient) })
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("info")
                    .then(stringArg("id").executes { ctx -> groupInfo(ctx, apiClient) })
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("create")
                    .then(
                        stringArg("id").then(
                            stringArg("name").executes { ctx -> createGroup(ctx, apiClient) }
                        )
                    )
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("delete")
                    .then(stringArg("id").executes { ctx -> deleteGroup(ctx, apiClient) })
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("addperm")
                    .then(
                        stringArg("id").then(
                            stringArg("permission").executes { ctx -> addPermission(ctx, apiClient) }
                        )
                    )
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("removeperm")
                    .then(
                        stringArg("id").then(
                            stringArg("permission").executes { ctx -> removePermission(ctx, apiClient) }
                        )
                    )
            )
    }

    private fun createPlayerNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("player")
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("get")
                    .then(stringArg("name").executes { ctx -> getPlayerPermission(ctx, apiClient) })
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("set")
                    .then(
                        stringArg("name").then(
                            stringArg("group").then(
                                stringArg("duration").executes { ctx -> setPlayerGroup(ctx, apiClient) }
                            )
                        )
                    )
            )
    }

    private fun listGroups(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source

        apiClient.listPermissionGroups().thenAccept { groups ->
            if (groups.isEmpty()) {
                OgCloudCommand.sendPrefixed(source, "No permission groups found.")
                return@thenAccept
            }

            OgCloudCommand.sendPrefixed(source, "&fPermission Groups (${groups.size}):")

            groups.forEach { group ->
                val defaultMarker = if (group.default) " &a[default]" else ""
                OgCloudCommand.sendMessage(
                    source,
                    " &8- &f${group.id} &7(${group.name}) weight: ${group.weight}$defaultMarker &8perms: &f${group.permissions.size}"
                )
            }
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun groupInfo(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)

        apiClient.getPermissionGroup(groupId).thenAccept { group ->
            OgCloudCommand.sendPrefixed(source, "&fPermission Group: ${group.id}")
            OgCloudCommand.sendMessage(source, " &7Name: &f${group.name}")
            OgCloudCommand.sendMessage(source, " &7Weight: &f${group.weight}")
            OgCloudCommand.sendMessage(source, " &7Default: &f${group.default}")
            OgCloudCommand.sendMessage(source, " &7Prefix: &f${group.display.chatPrefix}")
            OgCloudCommand.sendMessage(source, " &7Suffix: &f${group.display.chatSuffix}")
            OgCloudCommand.sendMessage(source, " &7Permissions (${group.permissions.size}):")
            group.permissions.forEach { permission ->
                OgCloudCommand.sendMessage(source, "   &8- &f$permission")
            }
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun createGroup(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)
        val groupName = ctx.getArgument("name", String::class.java)

        apiClient.createPermissionGroup(mapOf("id" to groupId, "name" to groupName)).thenAccept { group ->
            OgCloudCommand.sendPrefixed(source, "&aPermission group '${group.id}' created.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun deleteGroup(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)

        apiClient.deletePermissionGroup(groupId).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aPermission group '$groupId' deleted.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun addPermission(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)
        val permission = ctx.getArgument("permission", String::class.java)

        apiClient.addPermission(groupId, permission).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aPermission '$permission' added to group '$groupId'.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun removePermission(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)
        val permission = ctx.getArgument("permission", String::class.java)

        apiClient.removePermission(groupId, permission).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aPermission '$permission' removed from group '$groupId'.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun getPlayerPermission(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val playerName = ctx.getArgument("name", String::class.java)

        resolveOnlinePlayerUuid(apiClient, source, playerName)
            .thenCompose { uuid -> uuid?.let(apiClient::getPlayer) ?: CompletableFuture.completedFuture(null) }
            .thenAccept { player ->
                if (player == null) {
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixed(source, "&fPlayer: ${player.name}")
                OgCloudCommand.sendMessage(source, " &7Group: &f${player.permission.group}")

                val duration = if (player.permission.length == PERMANENT_LENGTH) {
                    "permanent"
                } else {
                    "${player.permission.length}ms"
                }

                OgCloudCommand.sendMessage(source, " &7Duration: &f$duration")
            }
            .exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }

    private fun setPlayerGroup(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val playerName = ctx.getArgument("name", String::class.java)
        val group = ctx.getArgument("group", String::class.java)
        val duration = ctx.getArgument("duration", String::class.java)

        resolveOnlinePlayerUuid(apiClient, source, playerName)
            .thenCompose { uuid ->
                uuid?.let { apiClient.setPlayerGroup(it, group, duration) } ?: CompletableFuture.completedFuture(null)
            }
            .thenAccept { player ->
                if (player == null) {
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixed(source, "&aSet ${player.name}'s group to '${player.permission.group}'.")
            }
            .exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }

    private fun resolveOnlinePlayerUuid(
        apiClient: ApiClient,
        source: CommandSource,
        playerName: String
    ): CompletableFuture<String?> {
        return apiClient.listOnlinePlayers(name = playerName).thenApply { players ->
            val player = players.firstOrNull()

            if (player == null) {
                OgCloudCommand.sendError(source, "Player '$playerName' not found online.")
                return@thenApply null
            }

            player.uuid
        }
    }

    private fun stringArg(name: String): RequiredArgumentBuilder<CommandSource, String> {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.word())
    }

    private const val PERMANENT_LENGTH = -1L
}
