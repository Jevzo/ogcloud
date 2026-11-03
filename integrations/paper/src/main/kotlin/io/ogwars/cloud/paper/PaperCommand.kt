package io.ogwars.cloud.paper

import io.ogwars.cloud.node.core.command.CommandSender
import io.ogwars.cloud.node.core.command.CommandTree
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import java.util.UUID

/** `/ogcloud` and `/oc` on the backend (v1 bug 4: no more `/pogcloud`); node-core's tree checks each node itself. */
internal class PaperCommand(
    private val tree: CommandTree,
) : BasicCommand {
    override fun execute(
        source: CommandSourceStack,
        args: Array<String>,
    ) = tree.execute(Sender(source.sender), args.toList())

    override fun suggest(
        source: CommandSourceStack,
        args: Array<String>,
    ): Collection<String> = tree.complete(Sender(source.sender), if (args.isEmpty()) listOf("") else args.toList())

    private class Sender(
        private val sender: org.bukkit.command.CommandSender,
    ) : CommandSender {
        override val player: UUID? = (sender as? Player)?.uniqueId

        override fun hasPermission(node: String) = sender.hasPermission(node)

        override fun reply(text: String) = sender.sendMessage(legacyText(text))
    }
}
