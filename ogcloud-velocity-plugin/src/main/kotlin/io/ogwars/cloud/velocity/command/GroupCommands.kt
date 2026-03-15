package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.message.VelocityMessages
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource

object GroupCommands {
    fun create(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("group")
            .then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("list")
                    .executes { ctx ->
                        listGroups(ctx, apiClient)
                        return@executes 1
                    },
            ).then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("info")
                    .then(
                        OgCloudCommand.wordArg("id").executes { ctx ->
                            groupInfo(ctx, apiClient)
                            return@executes 1
                        },
                    ),
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("maintenance").then(
                    OgCloudCommand.wordArg("id").then(
                        RequiredArgumentBuilder
                            .argument<CommandSource, Boolean>("enabled", BoolArgumentType.bool())
                            .executes { ctx ->
                                setMaintenance(ctx, apiClient)
                                return@executes 1
                            },
                    ),
                ),
            )

    private fun listGroups(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ) {
        val source = ctx.source

        OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Group.LIST_FETCHING)

        apiClient
            .listGroups()
            .thenAccept { groups ->
                if (groups.isEmpty()) {
                    OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Group.LIST_EMPTY)
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Group.LIST_HEADER,
                    "count" to groups.size,
                )

                groups.forEach { group ->
                    val maintenanceMarker =
                        if (group.maintenance) {
                            VelocityMessages.Command.Group.LIST_MAINTENANCE_MARKER
                        } else {
                            ""
                        }

                    OgCloudCommand.sendMessage(
                        source,
                        OgCloudCommand.format(
                            VelocityMessages.Command.Group.LIST_ENTRY,
                            "group_id" to group.id,
                            "group_type" to group.type,
                            "maintenance_marker" to maintenanceMarker,
                            "min_online" to group.scaling.minOnline,
                            "max_instances" to group.scaling.maxInstances,
                        ),
                    )
                }
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }
    }

    private fun groupInfo(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ) {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)

        apiClient
            .getGroup(groupId)
            .thenAccept { group ->
                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Group.INFO_HEADER,
                    "group_id" to group.id,
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(VelocityMessages.Command.Group.INFO_TYPE, "group_type" to group.type),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Group.INFO_MAINTENANCE,
                        "maintenance" to group.maintenance,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Group.INFO_INSTANCES,
                        "min_online" to group.scaling.minOnline,
                        "max_instances" to group.scaling.maxInstances,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Group.INFO_TEMPLATE,
                        "template_path" to group.templatePath,
                        "template_version" to group.templateVersion,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Group.INFO_IMAGE,
                        "server_image" to group.serverImage,
                    ),
                )
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }
    }

    private fun setMaintenance(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ) {
        val source = ctx.source
        val groupId = ctx.getArgument("id", String::class.java)
        val enabled = ctx.getArgument("enabled", Boolean::class.java)

        OgCloudCommand.sendPrefixedTemplate(
            source,
            VelocityMessages.Command.Group.MAINTENANCE_SETTING,
            "group_id" to groupId,
            "enabled" to enabled,
        )

        apiClient
            .setGroupMaintenance(groupId, enabled)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Group.MAINTENANCE_UPDATED)
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }
    }
}
