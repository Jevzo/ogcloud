package io.ogwars.cloud.velocity.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.server.ServerRegistry

object ServerCommands {

    fun create(apiClient: ApiClient, serverRegistry: ServerRegistry): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("server")
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("list").then(
                    OgCloudCommand.wordArg("group").executes { ctx ->
                        listServers(
                            ctx, apiClient, ctx.getArgument("group", String::class.java)
                        )
                    }).executes { ctx -> listServers(ctx, apiClient, null) }).then(
                LiteralArgumentBuilder.literal<CommandSource>("info")
                    .then(OgCloudCommand.wordArg("server").suggests { _, builder ->
                        serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }.executes { ctx -> serverInfo(ctx, apiClient, serverRegistry) })
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("request").then(
                    OgCloudCommand.wordArg("group").executes { ctx -> requestServer(ctx, apiClient) })
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("stop")
                    .then(OgCloudCommand.wordArg("server").suggests { _, builder ->
                        serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }.executes { ctx -> stopServer(ctx, apiClient, serverRegistry) })
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("kill")
                    .then(OgCloudCommand.wordArg("server").suggests { _, builder ->
                        serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }.executes { ctx -> killServer(ctx, apiClient, serverRegistry) })
            ).then(
                LiteralArgumentBuilder.literal<CommandSource>("templatepush")
                    .then(OgCloudCommand.wordArg("server").suggests { _, builder ->
                        serverRegistry.getAllDisplayNames().values.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }.executes { ctx -> templatePush(ctx, apiClient, serverRegistry) })
            )
    }

    private fun listServers(ctx: CommandContext<CommandSource>, apiClient: ApiClient, group: String?): Int {
        val source = ctx.source

        OgCloudCommand.sendPrefixed(source, "Fetching servers...")

        apiClient.listServers(group).thenAccept { servers ->
            if (servers.isEmpty()) {
                OgCloudCommand.sendPrefixed(source, "No servers found.")
                return@thenAccept
            }

            OgCloudCommand.sendPrefixed(source, "&fServers (${servers.size}):")

            for (s in servers) {
                OgCloudCommand.sendMessage(
                    source, " &8- &f${s.displayName} &7[${s.state}] &8players: &f${s.playerCount}"
                )
            }
        }.exceptionally { e ->
            OgCloudCommand.sendFailure(source, e)
            null
        }

        return 1
    }

    private fun resolveServerId(source: CommandSource, input: String, serverRegistry: ServerRegistry): String? {
        val serverId = serverRegistry.findServerIdByDisplayName(input)

        if (serverId == null) {
            OgCloudCommand.sendError(source, "Server '$input' not found.")
        }

        return serverId
    }

    private fun serverInfo(
        ctx: CommandContext<CommandSource>, apiClient: ApiClient, serverRegistry: ServerRegistry
    ): Int {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return 1

        apiClient.getServer(serverId).thenAccept { s ->
            OgCloudCommand.sendPrefixed(source, "&fServer: ${s.displayName}")
            OgCloudCommand.sendMessage(source, " &7ID: &f${s.id}")
            OgCloudCommand.sendMessage(source, " &7Group: &f${s.group}")
            OgCloudCommand.sendMessage(source, " &7State: &f${s.state}")
            OgCloudCommand.sendMessage(source, " &7Players: &f${s.playerCount}/${s.maxPlayers}")
            OgCloudCommand.sendMessage(source, " &7TPS: &f${String.format("%.1f", s.tps)}")
            OgCloudCommand.sendMessage(source, " &7Memory: &f${s.memoryUsedMb}MB")
            OgCloudCommand.sendMessage(source, " &7Pod: &f${s.podName} &7(${s.podIp ?: "no IP"})")
        }.exceptionally { e ->
            OgCloudCommand.sendFailure(source, e)
            null
        }

        return 1
    }

    private fun requestServer(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val group = ctx.getArgument("group", String::class.java)

        OgCloudCommand.sendPrefixed(source, "Requesting server in group '$group'...")

        apiClient.requestServer(group).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aServer requested successfully.")
        }.exceptionally { e ->
            OgCloudCommand.sendFailure(source, e)
            null
        }

        return 1
    }

    private fun stopServer(
        ctx: CommandContext<CommandSource>, apiClient: ApiClient, serverRegistry: ServerRegistry
    ): Int {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return 1
        val displayName = serverRegistry.getDisplayName(serverId) ?: input

        OgCloudCommand.sendPrefixed(source, "Stopping server '$displayName'...")

        apiClient.stopServer(serverId).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aServer stop requested.")
        }.exceptionally { e ->
            OgCloudCommand.sendFailure(source, e)
            null
        }

        return 1
    }

    private fun killServer(
        ctx: CommandContext<CommandSource>, apiClient: ApiClient, serverRegistry: ServerRegistry
    ): Int {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return 1
        val displayName = serverRegistry.getDisplayName(serverId) ?: input

        OgCloudCommand.sendPrefixed(source, "Killing server '$displayName'...")

        apiClient.killServer(serverId).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aServer killed.")
        }.exceptionally { e ->
            OgCloudCommand.sendFailure(source, e)
            null
        }

        return 1
    }

    private fun templatePush(
        ctx: CommandContext<CommandSource>, apiClient: ApiClient, serverRegistry: ServerRegistry
    ): Int {
        val source = ctx.source
        val input = ctx.getArgument("server", String::class.java)
        val serverId = resolveServerId(source, input, serverRegistry) ?: return 1
        val displayName = serverRegistry.getDisplayName(serverId) ?: input

        OgCloudCommand.sendPrefixed(source, "Forcing template push for '$displayName'...")

        apiClient.forceTemplatePush(serverId).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aTemplate push requested.")
        }.exceptionally { e ->
            OgCloudCommand.sendError(source, "Failed: ${e.message}")
            null
        }

        return 1
    }
}
