package io.ogwars.cloud.node.core.command

import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import java.util.UUID

/** Who runs a command: a player, or the console when [player] is null. Replies are `&`-coded. */
interface CommandSender {
    val player: UUID?

    fun hasPermission(node: String): Boolean

    fun reply(text: String)
}

/** One argument of a subcommand. */
class Argument(
    val name: String,
    val kind: Kind = Kind.WORD,
    val optional: Boolean = false,
    /** Tab completions for this argument. */
    val suggestions: (CommandSender) -> List<String> = { emptyList() },
) {
    enum class Kind { WORD, BOOLEAN, REST }

    val usage: String = if (optional) "[$name]" else "<$name>"
}

/** One subcommand: its words, its arguments, the node it needs and what it does. */
class Subcommand(
    val path: List<String>,
    val arguments: List<Argument>,
    val permission: String,
    /** Admin changes need an acting player for the gateway's checks (D-0115, D-0132). */
    val playersOnly: Boolean = false,
    val run: (Invocation) -> Unit,
) {
    val usage: String = (path + arguments.map { it.usage }).joinToString(" ")
}

/** A parsed call: the sender and the arguments by name. */
class Invocation(
    val sender: CommandSender,
    private val values: Map<String, String>,
) {
    fun word(name: String): String = values.getValue(name)

    fun optional(name: String): String? = values[name]

    fun bool(name: String): Boolean = values.getValue(name).toBooleanStrict()

    /** The acting player; only [Subcommand.playersOnly] subcommands may ask. */
    val player: UUID get() = checkNotNull(sender.player) { "Only player commands have an acting player" }
}

/**
 * A command made of subcommands, parsed the same on every platform. Adapters pass the words after the command name
 * and register [complete] for tab completion.
 */
class CommandTree(
    private val messages: Messages,
    private val subcommands: List<Subcommand>,
    private val info: Message,
) {
    fun execute(
        sender: CommandSender,
        words: List<String>,
    ) {
        val subcommand = subcommands.filter { matches(it.path, words) }.maxByOrNull { it.path.size }
        if (subcommand == null) return help(sender)
        when {
            !sender.hasPermission(subcommand.permission) -> {
                sender.reply(messages.text(Message.NO_PERMISSION))
            }

            subcommand.playersOnly && sender.player == null -> {
                sender.reply(messages.text(Message.PLAYERS_ONLY))
            }

            else -> {
                val values = parse(subcommand.arguments, words.drop(subcommand.path.size))
                if (values == null) {
                    sender.reply(messages.text(Message.USAGE, "usage" to subcommand.usage))
                } else {
                    subcommand.run(Invocation(sender, values))
                }
            }
        }
    }

    /** Completions for the last, partly typed word. */
    fun complete(
        sender: CommandSender,
        words: List<String>,
    ): List<String> {
        val typed = words.dropLast(1)
        val partial = words.lastOrNull().orEmpty()
        return subcommands
            .filter { sender.hasPermission(it.permission) }
            .flatMap { candidates(it, sender, typed) }
            .filter { it.startsWith(partial, ignoreCase = true) }
            .distinct()
            .sorted()
    }

    private fun candidates(
        subcommand: Subcommand,
        sender: CommandSender,
        typed: List<String>,
    ): List<String> {
        if (typed.size < subcommand.path.size) {
            return if (matches(typed, subcommand.path)) listOf(subcommand.path[typed.size]) else emptyList()
        }
        if (!matches(subcommand.path, typed)) return emptyList()
        val argument =
            subcommand.arguments.getOrNull(typed.size - subcommand.path.size) ?: subcommand.arguments.lastOrNull()
        return when {
            argument == null -> emptyList()
            argument.kind == Argument.Kind.BOOLEAN -> listOf("true", "false")
            else -> argument.suggestions(sender)
        }
    }

    private fun help(sender: CommandSender) {
        sender.reply(messages.text(info))
        val usable = subcommands.filter { sender.hasPermission(it.permission) }
        if (usable.isEmpty()) return
        sender.reply(messages.text(Message.HELP_HEADER))
        usable.forEach { sender.reply(messages.text(Message.HELP_ENTRY, "usage" to it.usage)) }
    }

    /** True when [words] start with every word of [prefix], ignoring case. */
    private fun matches(
        prefix: List<String>,
        words: List<String>,
    ) = words.size >= prefix.size && prefix.indices.all { prefix[it].equals(words[it], ignoreCase = true) }

    /** The arguments by name, or null when the words do not fit them. */
    private fun parse(
        arguments: List<Argument>,
        words: List<String>,
    ): Map<String, String>? {
        val values = mutableMapOf<String, String>()
        var next = 0
        for (argument in arguments) {
            if (argument.kind == Argument.Kind.REST) {
                val rest = words.drop(next).joinToString(" ")
                if (rest.isEmpty() && !argument.optional) return null
                if (rest.isNotEmpty()) values[argument.name] = rest
                next = words.size
                continue
            }
            val word = words.getOrNull(next++) ?: if (argument.optional) continue else return null
            if (argument.kind == Argument.Kind.BOOLEAN && word.toBooleanStrictOrNull() == null) return null
            values[argument.name] = word
        }
        return values.takeIf { next >= words.size }
    }
}
