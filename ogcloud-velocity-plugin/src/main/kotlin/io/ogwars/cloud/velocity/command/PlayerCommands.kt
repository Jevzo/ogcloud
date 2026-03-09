package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer

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
                    OgCloudCommand.sendPrefixed(source, "Player '$name' not found online.")
                    return@thenAccept
                }

                val p = players.first()

                OgCloudCommand.sendPrefixed(source, "&fPlayer: ${p.name}")
                OgCloudCommand.sendMessage(source, " &7UUID: &f${p.uuid}")
                OgCloudCommand.sendMessage(source, " &7Server: &f${p.serverDisplayName ?: p.serverId ?: "none"}")
                OgCloudCommand.sendMessage(source, " &7Proxy: &f${p.proxyDisplayName ?: p.proxyId ?: "none"}")
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
                    OgCloudCommand.sendError(source, "Server '$serverInput' not found.")
                    return 1
                }

                resolved
            } else {
                null
            }

        OgCloudCommand.sendPrefixed(source, "Fetching players...")

        apiClient
            .listOnlinePlayers(serverId = serverId)
            .thenAccept { players ->
                if (players.isEmpty()) {
                    OgCloudCommand.sendPrefixed(source, "No online players found.")
                    return@thenAccept
                }

                OgCloudCommand.sendPrefixed(source, "&fOnline players (${players.size}):")

                for (p in players) {
                    OgCloudCommand.sendMessage(
                        source,
                        " &8- &f${p.name} &7server: &f${p.serverDisplayName ?: p.serverId ?: "none"}",
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
                    OgCloudCommand.sendError(source, "Player '$name' not found online.")
                    return@thenCompose java.util.concurrent.CompletableFuture
                        .completedFuture(null)
                }

                val uuid = players.first().uuid

                OgCloudCommand.sendPrefixed(source, "Transferring '$name' to '$target'...")

                apiClient.transferPlayer(uuid, target)
            }.thenAccept {
                OgCloudCommand.sendPrefixed(source, "&aTransfer requested.")
            }.exceptionally { e ->
                OgCloudCommand.sendFailure(source, e)
                null
            }

        return 1
    }
}
