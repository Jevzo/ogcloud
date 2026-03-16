package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.message.VelocityMessages
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource

object NetworkCommands {
    fun create(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("network")
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("maintenance").then(
                    RequiredArgumentBuilder
                        .argument<CommandSource, Boolean>("enabled", BoolArgumentType.bool())
                        .executes { ctx ->
                            setMaintenance(ctx, apiClient)
                            return@executes 1
                        },
                ),
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("info").executes { ctx ->

                    networkInfo(ctx, apiClient)
                    return@executes 1
                },
            )

    private fun setMaintenance(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ) {
        val source = ctx.source
        val enabled = ctx.getArgument("enabled", Boolean::class.java)

        OgCloudCommand.sendPrefixedTemplate(
            source,
            VelocityMessages.Command.Network.MAINTENANCE_SETTING,
            "enabled" to enabled,
        )

        apiClient
            .setNetworkMaintenance(enabled)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Network.MAINTENANCE_UPDATED)
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }
    }

    private fun networkInfo(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ) {
        val source = ctx.source

        apiClient
            .getNetworkSettings()
            .thenAccept { settings ->
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Network.INFO_HEADER)
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Network.INFO_MAINTENANCE,
                        "maintenance" to settings.maintenance,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Network.INFO_MAX_PLAYERS,
                        "max_players" to settings.maxPlayers,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Network.INFO_DEFAULT_GROUP,
                        "default_group" to settings.defaultGroup,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(VelocityMessages.Command.Network.INFO_MOTD, "motd" to settings.motd.global),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Network.INFO_VERSION,
                        "version" to settings.versionName.global,
                    ),
                )
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }
    }
}
