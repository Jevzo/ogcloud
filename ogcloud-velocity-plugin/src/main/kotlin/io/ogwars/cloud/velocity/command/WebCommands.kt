package io.ogwars.cloud.velocity.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.CommandSource
import io.ogwars.cloud.velocity.api.ApiClient

object WebCommands {

    fun create(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("web")
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("user")
                    .then(createListNode(apiClient))
                    .then(createCreateNode(apiClient))
                    .then(createDeleteNode(apiClient))
                    .then(createUpdateNode(apiClient))
            )
    }

    private fun createListNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("list")
            .executes { ctx -> listUsers(ctx, apiClient) }
    }

    private fun createCreateNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("create")
            .then(
                stringArg("email").then(
                    stringArg("password").then(
                        wordArg("role")
                            .suggests { _, builder ->
                                builder.suggest(ROLE_SERVICE)
                                builder.suggest(ROLE_ADMIN)
                                builder.suggest(ROLE_DEVELOPER)
                                builder.buildFuture()
                            }
                            .executes { ctx -> createUser(ctx, apiClient) }
                    )
                )
            )
    }

    private fun createDeleteNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("delete")
            .then(stringArg("email").executes { ctx -> deleteUser(ctx, apiClient) })
    }

    private fun createUpdateNode(apiClient: ApiClient): LiteralArgumentBuilder<CommandSource> {
        return LiteralArgumentBuilder.literal<CommandSource>("update")
            .then(
                stringArg("targetEmail")
                    .then(updateFieldNode("email", apiClient))
                    .then(updateFieldNode("password", apiClient))
                    .then(updateFieldNode("username", apiClient))
                    .then(updateFieldNode("role", apiClient, suggestedRoles = true))
            )
    }

    private fun updateFieldNode(
        field: String,
        apiClient: ApiClient,
        suggestedRoles: Boolean = false
    ): LiteralArgumentBuilder<CommandSource> {
        val valueArg = if (field == "role") wordArg("value") else stringArg("value")

        if (suggestedRoles) {
            valueArg.suggests { _, builder ->
                builder.suggest(ROLE_SERVICE)
                builder.suggest(ROLE_ADMIN)
                builder.suggest(ROLE_DEVELOPER)
                builder.buildFuture()
            }
        }

        return LiteralArgumentBuilder.literal<CommandSource>(field)
            .then(valueArg.executes { ctx -> updateUser(ctx, apiClient, field) })
    }

    private fun listUsers(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source

        apiClient.listWebUsers().thenAccept { users ->
            if (users.isEmpty()) {
                OgCloudCommand.sendPrefixed(source, "&7No web users found.")
                return@thenAccept
            }

            OgCloudCommand.sendPrefixed(source, "&6Web Users &8(${users.size})")

            users.forEach { user ->
                OgCloudCommand.sendMessage(
                    source,
                    " &8- &7${user.email} &8| &f${user.username} &8| &e${user.role}"
                )
            }
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun createUser(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val email = ctx.getArgument("email", String::class.java)
        val password = ctx.getArgument("password", String::class.java)
        val role = ctx.getArgument("role", String::class.java)

        apiClient.createWebUser(email, password, role).thenAccept { user ->
            OgCloudCommand.sendPrefixed(source, "&aCreated web user ${user.email} with role ${user.role}.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun deleteUser(ctx: CommandContext<CommandSource>, apiClient: ApiClient): Int {
        val source = ctx.source
        val email = ctx.getArgument("email", String::class.java)

        apiClient.deleteWebUser(email).thenAccept {
            OgCloudCommand.sendPrefixed(source, "&aDeleted web user $email.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun updateUser(ctx: CommandContext<CommandSource>, apiClient: ApiClient, field: String): Int {
        val source = ctx.source
        val targetEmail = ctx.getArgument("targetEmail", String::class.java)
        val value = ctx.getArgument("value", String::class.java)

        apiClient.updateWebUser(targetEmail, mapOf(field to value)).thenAccept { user ->
            OgCloudCommand.sendPrefixed(source, "&aUpdated $field for ${user.email}.")
        }.exceptionally { error ->
            OgCloudCommand.sendFailure(source, error)
            null
        }

        return 1
    }

    private fun stringArg(name: String): RequiredArgumentBuilder<CommandSource, String> {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.string())
    }

    private fun wordArg(name: String): RequiredArgumentBuilder<CommandSource, String> {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.word())
    }

    private const val ROLE_SERVICE = "SERVICE"
    private const val ROLE_ADMIN = "ADMIN"
    private const val ROLE_DEVELOPER = "DEVELOPER"
}
