package io.ogwars.cloud.velocity.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.server.ServerRegistry
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object OgCloudCommand {
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    fun register(
        proxyServer: ProxyServer,
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ) {
        val node =
            LiteralArgumentBuilder
                .literal<CommandSource>("ogcloud")
                .then(
                    ServerCommands
                        .create(apiClient, serverRegistry)
                        .requires { it.hasPermission("ogcloud.admin.server") },
                ).then(GroupCommands.create(apiClient).requires { it.hasPermission("ogcloud.admin.group") })
                .then(
                    PlayerCommands
                        .create(apiClient, proxyServer, serverRegistry)
                        .requires { it.hasPermission("ogcloud.admin.player") },
                ).then(NetworkCommands.create(apiClient).requires { it.hasPermission("ogcloud.admin.network") })
                .then(PermCommands.create(apiClient).requires { it.hasPermission("ogcloud.admin.permission") })
                .then(
                    CommandCommand
                        .create(apiClient, serverRegistry)
                        .requires { it.hasPermission("ogcloud.admin.command") },
                ).executes(::sendOgCloudInfoMessage)
                .build()

        val command = BrigadierCommand(node)
        proxyServer.commandManager.register(
            proxyServer.commandManager
                .metaBuilder(command)
                .aliases("oc")
                .build(),
            command,
        )
    }

    fun sendOgCloudInfoMessage(ctx: CommandContext<CommandSource>): Int {
        sendPrefixed(ctx.source, "This server is running on OgCloud <https://ogcloud.dev/>")
        return 1
    }

    fun sendMessage(
        source: CommandSource,
        message: String,
    ) {
        source.sendMessage(legacySerializer.deserialize(message))
    }

    fun sendPrefixed(
        source: CommandSource,
        message: String,
    ) {
        sendMessage(source, "$PREFIX&7$message")
    }

    fun sendError(
        source: CommandSource,
        message: String,
    ) {
        sendMessage(source, "$PREFIX&c$message")
    }

    fun sendFailure(
        source: CommandSource,
        error: Throwable,
    ) {
        val message = generateSequence(error) { it.cause }.firstNotNullOfOrNull(Throwable::message) ?: "Unknown error"

        sendError(source, "Failed: $message")
    }

    fun wordArg(name: String): RequiredArgumentBuilder<CommandSource, String> =
        RequiredArgumentBuilder.argument(name, StringArgumentType.word())

    private const val PREFIX = "&8| &6OgCloud &7> "
}
