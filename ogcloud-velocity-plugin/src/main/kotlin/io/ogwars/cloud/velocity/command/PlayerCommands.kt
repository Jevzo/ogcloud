package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import java.util.concurrent.CompletableFuture

object PlayerCommands {
    fun create(
        apiClient: ApiClient,
        proxyServer: ProxyServer,
        serverRegistry: ServerRegistry,
    ): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder
            .literal<CommandSource>("player")
            .then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("find")
                    .then(
                        OgCloudCommand
                            .wordArg("name")
                            .suggests { _, builder ->
                                proxyServer.allPlayers.forEach { builder.suggest(it.username) }
                                builder.buildFuture()
                            }.executes { ctx -> findPlayer(ctx, apiClient) },
                    ),
            ).then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("list")
                    .then(
                        OgCloudCommand
                            .wordArg("server")
                            .suggests { _, builder ->
                                serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }.executes { ctx ->
                                listPlayers(
                                    ctx,
                                    apiClient,
                                    serverRegistry,
                                    ctx.getArgument("server", String::class.java),
                                )
                            },
                    ).executes { ctx -> listPlayers(ctx, apiClient, serverRegistry, null) },
            ).then(
                LiteralArgumentBuilder
                    .literal<CommandSource>("transfer")
                    .then(
                        OgCloudCommand
                            .wordArg("name")
                            .suggests { _, builder ->
                                proxyServer.allPlayers.forEach { builder.suggest(it.username) }
                                builder.buildFuture()
                            }.then(
                                OgCloudCommand
                                    .wordArg("target")
                                    .suggests { _, builder ->
                                        serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }.executes { ctx -> transferPlayer(ctx, apiClient) },
                            ),
                    ),
            )

    private fun findPlayer(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source
        val name = ctx.getArgument("name", String::class.java)

        apiClient
            .listOnlinePlayers(name = name)
            .thenAccept { players ->
                if (players.isEmpty()) {
                    OgCloudCommand.sendPrefixedTemplate(
                        source,
                        VelocityMessages.Command.Player.FIND_NOT_FOUND,
                        "player_name" to name,
                    )
                    return@thenAccept
                }

                val p = players.first()
                val server = p.serverDisplayName ?: p.serverId ?: VelocityMessages.Common.NONE
                val proxy = p.proxyDisplayName ?: p.proxyId ?: VelocityMessages.Common.NONE

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Player.FIND_HEADER,
                    "player_name" to p.name,
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(VelocityMessages.Command.Player.FIND_UUID, "uuid" to p.uuid),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(VelocityMessages.Command.Player.FIND_SERVER, "server" to server),
                )
                OgCloudCommand.sendMessage(
                    source,
                    OgCloudCommand.format(VelocityMessages.Command.Player.FIND_PROXY, "proxy" to proxy),
                )
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }

        return 1
    }

    private fun listPlayers(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
        serverInput: String?,
    ): Int {
        val source = ctx.source

        val serverId =
            if (serverInput != null) {
                val resolved = serverRegistry.findServerIdByDisplayName(serverInput)

                if (resolved == null) {
                    OgCloudCommand.sendErrorTemplate(
                        source,
                        VelocityMessages.Command.Player.LIST_SERVER_NOT_FOUND,
                        "server" to serverInput,
                    )
                    return 1
                }

                resolved
            } else {
                null
            }

        OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Player.LIST_FETCHING)

        apiClient
            .listOnlinePlayers(serverId = serverId)
            .thenAccept { players ->
                if (players.isEmpty()) {
                    OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Player.LIST_EMPTY)
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Player.LIST_HEADER,
                    "count" to players.size,
                )

                for (p in players) {
                    val server = p.serverDisplayName ?: p.serverId ?: VelocityMessages.Common.NONE
                    OgCloudCommand.sendMessage(
                        source,
                        OgCloudCommand.format(
                            VelocityMessages.Command.Player.LIST_ENTRY,
                            "player_name" to p.name,
                            "server" to server,
                        ),
                    )
                }
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }

        return 1
    }

    private fun transferPlayer(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
    ): Int {
        val source = ctx.source
        val name = ctx.getArgument("name", String::class.java)
        val target = ctx.getArgument("target", String::class.java)

        apiClient
            .listOnlinePlayers(name = name)
            .thenCompose { players ->
                if (players.isEmpty()) {
                    OgCloudCommand.sendErrorTemplate(
                        source,
                        VelocityMessages.Command.Player.TRANSFER_NOT_FOUND,
                        "player_name" to name,
                    )
                    return@thenCompose CompletableFuture.completedFuture(null)
                }

                val uuid = players.first().uuid

                OgCloudCommand.sendPrefixedTemplate(
                    source,
                    VelocityMessages.Command.Player.TRANSFER_REQUESTING,
                    "player_name" to name,
                    "target" to target,
                )

                apiClient.transferPlayer(uuid, target)
            }.thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.Player.TRANSFER_REQUESTED)
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }

        return 1
    }
}
