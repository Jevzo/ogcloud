package io.ogwars.cloud.velocity.command

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import io.ogwars.cloud.velocity.api.ApiClient

object GroupCommands {

    fun create(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("group")
            .then(LiteralArgumentBuilder.literal<CommandSource>("list").executes { ctx -> listGroups(ctx, apiClient) })
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("info")
                    .then(wordArg("id").executes { ctx -> groupInfo(ctx, apiClient) })
            )
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("maintenance")
                    .then(
                        wordArg("id").then(
                            RequiredArgumentBuilder.argument<CommandSource, Boolean>("enabled", BoolArgumentType.bool())
                                .executes { ctx -> setMaintenance(ctx, apiClient) }
                        )
                    )
            )
    }

    private fun listGroups(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source

        OgCloudCommand.sendPrefixed(source, "Fetching groups...")

        apiClient.listGroups().thenAccept { groups ->
            if (groups.isEmpty()) {
                OgCloudCommand.sendPrefixed(source, "No groups found.")
                return@thenAccept
            }

            OgCloudCommand.sendPrefixed(source, "&fGroups (${groups.size}):")

            groups.forEach { group ->
                val maintenanceMarker = if (group.maintenance) " &c[MAINT]" else ""
                OgCloudCommand.sendMessage(
                    source,
                    " &8- &f${group.id} &7(${group.type})$maintenanceMarker &8instances: &f${group.scaling.minOnline}-${group.scaling.maxInstances}"
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

        apiClient.getGroup(groupId).thenAccept { group ->
            OgCloudCommand.sendPrefixed(source, "&fGroup: ${group.id}")
            OgCloudCommand.sendMessage(source, " &7Type: &f${group.type}")
            OgCloudCommand.sendMessage(source, " &7Maintenance: &f${group.maintenance}")
            OgCloudCommand.sendMessage(source, " &7Instances: &f${group.scaling.minOnline}-${group.scaling.maxInstances}")
            OgCloudCommand.sendMessage(source, " &7Template: &f${group.templatePath}/${group.templateVersion}")
            OgCloudCommand.sendMessage(source, " &7Image: &f${group.serverImage}")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun setMaintenance(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)
        val enabled = ctx.getArgument("enabled", Boolean::class.java)

        OgCloudCommand.sendPrefixed(source, "Setting group '$groupId' maintenance to $enabled...")

        apiClient.setGroupMaintenance(groupId, enabled).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aGroup maintenance updated.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun wordArg(name: String): RequiredArgumentBuilder<CommandSource, String> {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.word())
    }
}
