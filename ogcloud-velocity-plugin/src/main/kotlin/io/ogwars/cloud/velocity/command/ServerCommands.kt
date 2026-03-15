package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource

object ServerCommands {
    fun create(
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("server")
            .then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("list")
                    .then(
                        OgCloudCommand.wordArg("group").executes { ctx ->
                            listServers(
                                ctx,
                                apiClient,
                                ctx.getArgument("group", String::class.java),
                            )
                            return@executes 1
                        },
                    ).executes { ctx ->
                        listServers(ctx, apiClient, null)
                        return@executes 1
                    },
            ).then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("info")
                    .then(
                        OgCloudCommand
                            .wordArg("server")
                            .suggests { _, builder ->
                                serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }.executes { ctx ->
                                serverInfo(ctx, apiClient, serverRegistry)
                                return@executes 1
                            },
                    ),
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("request").then(
                    OgCloudCommand.wordArg("group").executes { ctx ->
                        requestServer(ctx, apiClient)
                        return@executes 1
                    },
                ),
            ).then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("stop")
                    .then(
                        OgCloudCommand
                            .wordArg("server")
                            .suggests { _, builder ->
                                serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }.executes { ctx ->
                                stopServer(ctx, apiClient, serverRegistry)
                                return@executes 1
                            },
                    ),
            ).then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("kill")
                    .then(
                        OgCloudCommand
                            .wordArg("server")
                            .suggests { _, builder ->
                                serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }.executes { ctx ->
                                killServer(ctx, apiClient, serverRegistry)
                                return@executes 1
                            },
                    ),
            ).then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("templatepush")
                    .then(
                        OgCloudCommand
                            .wordArg("server")
                            .suggests { _, builder ->
                                serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }.executes { ctx ->
                                templatePush(ctx, apiClient, serverRegistry)
                                return@executes 1
                            },
                    ),
            )

    private fun listServers(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
        group: String?,
    ) {
        val source = ctx.source

        OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Server.LIST_FETCHING)

        apiClient
            .listServers(group)
            .thenAccept { servers ->
                if (servers.isEmpty()) {
                    OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Server.LIST_EMPTY)
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Server.LIST_HEADER,
                    "count" to servers.size,
                )

                for (s in servers) {
                    OgCloudCommand.sendMessage(
                        source,
                        OgCloudCommand.format(
                            VelocityMessages.Command.Server.LIST_ENTRY,
                            "display_name" to s.displayName,
                            "state" to s.state,
                            "player_count" to s.playerCount,
                        ),
                    )
                }
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }
    }

    private fun resolveServerId(
        source: CommandSource,
        input: String,
        serverRegistry: ServerRegistry,
    ): String? {
        val serverId = serverRegistry.findServerIdByDisplayName(input)

        if (serverId == null) {
            OgCloudCommand.sendErrorTemplate(
                source,
                VelocityMessages.Command.Server.NOT_FOUND,
                "server" to input,
            )
        }

        return serverId
    }

    private fun serverInfo(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ) {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return

        apiClient
            .getServer(serverId)
            .thenAccept { s ->
                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Server.INFO_HEADER,
                    "display_name" to s.displayName,
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Server.INFO_ID,
                        "id" to s.id,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(VelocityMessages.Command.Server.INFO_GROUP, "group" to s.group),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(VelocityMessages.Command.Server.INFO_STATE, "state" to s.state),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Server.INFO_PLAYERS,
                        "online" to s.playerCount,
                        "max" to s.maxPlayers,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Server.INFO_TPS,
                        "tps" to String.format("%.1f", s.tps),
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Server.INFO_MEMORY,
                        "memory_mb" to s.memoryUsedMb,
                    ),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(
                        VelocityMessages.Command.Server.INFO_POD,
                        "pod_name" to s.podName,
                        "pod_ip" to (s.podIp ?: VelocityMessages.Common.NO_IP),
                    ),
                )
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }
    }

    private fun requestServer(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ) {
        val source = ctx.source
        val group = ctx.getArgument("group", String::class.java)

        OgCloudCommand.sendPrefixedTemplate(
            source,
            VelocityMessages.Command.Server.REQUEST_REQUESTING,
            "group" to group,
        )

        apiClient
            .requestServer(group)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Server.REQUEST_SUCCESS)
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }
    }

    private fun stopServer(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ) {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return
        val displayName = serverRegistry.getDisplayName(serverId) ?: input

        OgCloudCommand.sendPrefixedTemplate(
            source,
            VelocityMessages.Command.Server.STOP_REQUESTING,
            "display_name" to displayName,
        )

        apiClient
            .stopServer(serverId)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Server.STOP_SUCCESS)
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }
    }

    private fun killServer(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ) {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return
        val displayName = serverRegistry.getDisplayName(serverId) ?: input

        OgCloudCommand.sendPrefixedTemplate(
            source,
            VelocityMessages.Command.Server.KILL_REQUESTING,
            "display_name" to displayName,
        )

        apiClient
            .killServer(serverId)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Server.KILL_SUCCESS)
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }
    }

    private fun templatePush(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ) {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return
        val displayName = serverRegistry.getDisplayName(serverId) ?: input

        OgCloudCommand.sendPrefixedTemplate(
            source,
            VelocityMessages.Command.Server.TEMPLATE_PUSH_REQUESTING,
            "display_name" to displayName,
        )

        apiClient
            .forceTemplatePush(serverId)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Server.TEMPLATE_PUSH_SUCCESS)
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }
    }
}
