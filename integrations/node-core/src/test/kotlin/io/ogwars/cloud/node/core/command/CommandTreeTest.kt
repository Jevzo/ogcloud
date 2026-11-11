package io.ogwars.cloud.node.core.command

import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.text.RichText
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CommandTreeTest {
    private val messages = Messages.load()
    private val calls = mutableListOf<String>()
    private val tree =
        CommandTree(
            messages,
            listOf(
                Subcommand(
                    listOf("group", "info"),
                    listOf(
                        Argument("group", suggestions = {
                            listOf("lobby", "bedwars")
                        }),
                    ),
                    "a.group",
                ) {
                    calls += "info " + it.word("group")
                },
                Subcommand(
                    listOf("group", "maintenance"),
                    listOf(Argument("group"), Argument("enabled", Argument.Kind.BOOLEAN)),
                    "a.group",
                    playersOnly = true,
                ) { calls += "maintenance " + it.word("group") + " " + it.bool("enabled") },
                Subcommand(listOf("server", "list"), listOf(Argument("group", optional = true)), "a.server") {
                    calls += "list " + it.optional("group")
                },
                Subcommand(listOf("command", "all"), listOf(Argument("command", Argument.Kind.REST)), "a.command") {
                    calls += "all " + it.word("command")
                },
            ),
            Message.INFO,
        )

    private class Sender(
        override val player: UUID?,
        private val nodes: Set<String>,
    ) : CommandSender {
        val replies = mutableListOf<RichText>()

        override fun hasPermission(node: String) = node in nodes

        override fun reply(text: RichText) {
            replies += text
        }
    }

    private val admin = Sender(UUID.randomUUID(), setOf("a.group", "a.server", "a.command"))

    @Test
    fun `words select the subcommand and fill its arguments`() {
        tree.execute(admin, listOf("GROUP", "info", "lobby"))
        tree.execute(admin, listOf("group", "maintenance", "lobby", "true"))
        tree.execute(admin, listOf("server", "list"))
        tree.execute(admin, listOf("command", "all", "say", "hello", "world"))

        calls shouldContainExactly listOf("info lobby", "maintenance lobby true", "list null", "all say hello world")
    }

    @Test
    fun `wrong words show the usage, missing nodes and the console are refused`() {
        tree.execute(admin, listOf("group", "maintenance", "lobby", "maybe"))
        tree.execute(admin, listOf("group", "info", "lobby", "extra"))
        tree.execute(Sender(UUID.randomUUID(), emptySet()), listOf("group", "info", "lobby"))
        tree.execute(Sender(null, setOf("a.group")), listOf("group", "maintenance", "lobby", "true"))

        calls shouldBe emptyList()
        admin.replies shouldContainExactly
            listOf(
                messages.text(Message.USAGE, "usage" to "group maintenance <group> <enabled>"),
                messages.text(Message.USAGE, "usage" to "group info <group>"),
            )
    }

    @Test
    fun `help lists only what the sender may use`() {
        val viewer = Sender(UUID.randomUUID(), setOf("a.server"))

        tree.execute(viewer, emptyList())

        viewer.replies shouldContainExactly
            listOf(
                messages.text(Message.INFO),
                messages.text(Message.HELP_HEADER),
                messages.text(Message.HELP_ENTRY, "usage" to "server list [group]"),
            )
    }

    @Test
    fun `completion offers words, booleans and argument suggestions`() {
        tree.complete(admin, listOf("gr")) shouldContainExactly listOf("group")
        tree.complete(admin, listOf("group", "")) shouldContainExactly listOf("info", "maintenance")
        tree.complete(admin, listOf("group", "info", "b")) shouldContainExactly listOf("bedwars")
        tree.complete(admin, listOf("group", "maintenance", "lobby", "")) shouldContainExactly listOf("false", "true")
        tree.complete(Sender(null, emptySet()), listOf("")) shouldBe emptyList()
    }
}
