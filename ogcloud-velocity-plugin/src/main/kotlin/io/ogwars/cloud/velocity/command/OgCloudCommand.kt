package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
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
                .executes {
                    sendPrefixed(it.source, VelocityMessages.Command.OGCLOUD_INFO)
                    return@executes 1
                }.then(
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
                ).build()

        val command = BrigadierCommand(node)
        proxyServer.commandManager.register(
            proxyServer.commandManager
                .metaBuilder(command)
                .aliases("oc")
                .build(),
            command,
        )
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
        sendMessage(source, "${VelocityMessages.Prefix.COMMAND}&7$message")
    }

    fun sendError(
        source: CommandSource,
        message: String,
    ) {
        sendMessage(source, "${VelocityMessages.Prefix.COMMAND}&c$message")
    }

    fun sendPrefixedTemplate(
        source: CommandSource,
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ) {
        sendPrefixed(source, format(template, *placeholders))
    }

    fun sendErrorTemplate(
        source: CommandSource,
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ) {
        sendError(source, format(template, *placeholders))
    }

    fun sendFailure(
        source: CommandSource,
        error: Throwable,
    ) {
        val message =
            generateSequence(error) { it.cause }.firstNotNullOfOrNull(Throwable::message)
                ?: VelocityMessages.Command.FAILURE_UNKNOWN_ERROR

        sendErrorTemplate(source, VelocityMessages.Command.FAILURE_TEMPLATE, "error" to message)
    }

    fun wordArg(name: String): RequiredArgumentBuilder<CommandSource, String> =
        RequiredArgumentBuilder.argument(name, StringArgumentType.word())

    fun format(
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ): String = VelocityMessages.format(template, *placeholders)
}
