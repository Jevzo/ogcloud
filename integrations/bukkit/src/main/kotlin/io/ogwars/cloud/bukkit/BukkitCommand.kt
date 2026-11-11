package io.ogwars.cloud.bukkit

import io.ogwars.cloud.node.core.command.CommandTree
import io.ogwars.cloud.text.RichText
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import java.util.UUID

/** `/ogcloud` and `/oc` on a backend, declared in plugin.yml (v1 bug 4: no more `/pogcloud`). */
class BukkitCommand(
    private val tree: CommandTree,
    private val line: BukkitLine,
) : TabExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        tree.execute(Sender(sender, line), args.toList())
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): List<String> = tree.complete(Sender(sender, line), if (args.isEmpty()) listOf("") else args.toList())

    private class Sender(
        private val sender: CommandSender,
        private val line: BukkitLine,
    ) : io.ogwars.cloud.node.core.command.CommandSender {
        override val player: UUID? = (sender as? Player)?.uniqueId

        override fun hasPermission(node: String) = sender.hasPermission(node)

        override fun reply(text: RichText) = line.send(sender, text)
    }
}
