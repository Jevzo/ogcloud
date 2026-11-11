package io.ogwars.cloud.velocity

import io.ogwars.cloud.node.core.command.CommandSender
import io.ogwars.cloud.node.core.command.CommandTree
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.toComponent
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** `/ogcloud` and `/oc` on Velocity: node-core's command tree checks each subcommand's node itself. */
internal class OgCloudCommand(
    private val tree: CommandTree,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) =
        tree.execute(Sender(invocation.source()), invocation.arguments().toList())

    override fun suggestAsync(invocation: SimpleCommand.Invocation): CompletableFuture<List<String>> =
        CompletableFuture.completedFuture(tree.complete(Sender(invocation.source()), words(invocation.arguments())))

    private class Sender(
        private val source: CommandSource,
    ) : CommandSender {
        override val player: UUID? = (source as? Player)?.uniqueId

        override fun hasPermission(node: String) = source.hasPermission(node)

        override fun reply(text: RichText) = source.sendMessage(text.toComponent())
    }

    companion object {
        /** The typed words; nothing typed after the command means one empty word to complete. */
        fun words(arguments: Array<String>): List<String> = if (arguments.isEmpty()) listOf("") else arguments.toList()
    }
}
