package io.ogwars.cloud.velocity.command

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import io.ogwars.cloud.velocity.api.ApiClient

object NetworkCommands {
    fun create(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("network")
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("maintenance").then(
                    RequiredArgumentBuilder
                        .argument<CommandSource, Boolean>("enabled", BoolArgumentType.bool())
                        .executes { ctx -> setMaintenance(ctx, apiClient) },
                ),
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("info").executes { ctx ->
                    networkInfo(ctx, apiClient)
                },
            )

    private fun setMaintenance(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source
        val enabled = ctx.getArgument("enabled", Boolean::class.java)

        OgCloudCommand.sendPrefixed(source, "Setting network maintenance to $enabled...")

        apiClient
            .setNetworkMaintenance(enabled)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, "&aNetwork maintenance updated.")
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }

    private fun networkInfo(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source

        apiClient
            .getNetworkSettings()
            .thenAccept { settings ->
                OgCloudCommand.sendPrefixed(source, "&fNetwork Settings:")
                OgCloudCommand.sendMessage(source, " &7Maintenance: &f${settings.maintenance}")
                OgCloudCommand.sendMessage(source, " &7Max Players: &f${settings.maxPlayers}")
                OgCloudCommand.sendMessage(source, " &7Default Group: &f${settings.defaultGroup}")
                OgCloudCommand.sendMessage(source, " &7MOTD: &f${settings.motd.global}")
                OgCloudCommand.sendMessage(source, " &7Version: &f${settings.versionName.global}")
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }

        return 1
    }
}
