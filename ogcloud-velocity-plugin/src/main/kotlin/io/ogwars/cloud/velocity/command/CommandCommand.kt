package io.ogwars.cloud.velocity.command

import io.ogwars.cloud.velocity.api.ApiClient
import io.ogwars.cloud.velocity.message.VelocityMessages
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource

object CommandCommand {
    fun create(
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ): LiteralArgumentBuilder<CommandSource> =
        LiteralArgumentBuilder.literal<CommandSource>("command").then(
            OgCloudCommand
                .wordArg("target")
                .suggests { _, builder ->
                    builder.suggest(TARGET_ALL)
                    serverRegistry.getAllDisplayNames().values.forEach(builder::suggest)
                    builder.buildFuture()
                }.then(
                    RequiredArgumentBuilder
                        .argument<CommandSource, String>(
                            "cmd",
                            StringArgumentType.greedyString(),
                        ).executes { ctx ->
                            executeCommand(ctx, apiClient, serverRegistry)
                            return@executes 1
                        },
                ),
        )

    private fun executeCommand(
        ctx: CommandContext<CommandSource>,
        apiClient: ApiClient,
        serverRegistry: ServerRegistry,
    ) {
        val source = ctx.source
        val input = ctx.getArgument("target", String::class.java)
        val command = ctx.getArgument("cmd", String::class.java)
        val (targetType, target) = resolveTarget(input, serverRegistry)

        OgCloudCommand.sendPrefixedTemplate(
            source,
            VelocityMessages.Command.RemoteCommand.EXECUTE_REQUESTING,
            "command" to command,
            "target_type" to targetType,
            "target_input" to input,
        )

        apiClient
            .executeCommand(target, targetType, command)
            .thenAccept {
                OgCloudCommand.sendPrefixed(source, VelocityMessages.Command.RemoteCommand.EXECUTE_DISPATCHED)
            }.exceptionally { error ->
                OgCloudCommand.sendFailure(source, error)
                null
            }
    }

    private fun resolveTarget(
        input: String,
        serverRegistry: ServerRegistry,
    ): Pair<String, String> {
        if (input.equals(TARGET_ALL, ignoreCase = true)) {
            return TARGET_ALL to TARGET_ALL
        }

        val serverId = serverRegistry.findServerIdByDisplayName(input)
        if (serverId != null) {
            return TARGET_SERVER to serverId
        }

        return TARGET_GROUP to input
    }

    private const val TARGET_ALL = "all"
    private const val TARGET_SERVER = "server"
    private const val TARGET_GROUP = "group"
}
