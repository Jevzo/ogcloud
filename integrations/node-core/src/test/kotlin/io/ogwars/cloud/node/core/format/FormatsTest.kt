package io.ogwars.cloud.node.core.format

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.StoredTexts
import io.ogwars.cloud.text.NamedColor
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.TextField
import io.ogwars.cloud.text.endColor
import io.ogwars.cloud.text.toComponent
import io.ogwars.cloud.text.toLegacy
import io.ogwars.cloud.text.toPlain
import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveMaxLength
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.junit.jupiter.api.Test

class FormatsTest {
    private val chat = ChatFormat(Messages.load(), StoredTexts())

    private fun rank(group: PermissionGroup) = Rank(PlayerProfile.getDefaultInstance(), group)

    @Test
    fun `chat heads use the group's display and fill blanks from the catalog`() {
        val admin =
            PermissionGroup
                .newBuilder()
                .setChatPrefix("<red>[Admin] ")
                .setNameFormat("<gradient:#ff0000:#0000ff>{name}</gradient>")
                .setChatSuffix(" <dark_gray>» <white>")
                .build()

        val head = chat.head(rank(admin), "Jev")
        head.toPlain() shouldBe "[Admin] Jev » "
        head.toComponent() shouldBe
            Component
                .text()
                .append(Component.text("[Admin] ", NamedTextColor.RED))
                .append(Component.text("J", TextColor.color(0xff0000)))
                .append(Component.text("e", TextColor.color(0x800080)))
                .append(Component.text("v", TextColor.color(0x0000ff)))
                .append(Component.text(" "))
                .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                .build()
        head.endColor() shouldBe NamedColor.WHITE
        chat.head(rank(PermissionGroup.getDefaultInstance()), "Steve").toLegacy() shouldBe "§7Steve: "
        chat.head(null, "Steve").toLegacy() shouldBe "§7Steve: "
    }

    @Test
    fun `a name is plain text, whatever it looks like`() {
        chat.head(null, "<red>Eve").toLegacy() shouldBe "§7<red>Eve: "
    }

    @Test
    fun `tablist placeholders are v1's names in braces, filled once`() {
        val values = TablistValues("lobby-789abc", "lobby", "proxy-1", 12, 1000, 42, "{server}")

        val template =
            RichText.parseValid(
                "<gray>{server} {onlinePlayers}/{maxPlayers} {ping}ms {permissionGroup} {unknown}",
            )
        Tablist.fill(template, values).toPlain() shouldBe "lobby-789abc 12/1000 42ms {server} {unknown}"
    }

    @Test
    fun `the tablist fills every placeholder its texts offer`() {
        val values = TablistValues("s", "g", "p", 1, 2, 3, "r")
        listOf(TextField.TABLIST_HEADER, TextField.TABLIST_FOOTER).forEach { field ->
            val template = RichText.parseValid(field.samples.keys.joinToString(" ") { "{$it}" })
            Tablist.fill(template, values).toPlain() shouldBe "s g p 1 2 3 r"
        }
    }

    @Test
    fun `teams sort heaviest first, fit 16 characters and stay distinct`() {
        fun group(
            id: String,
            weight: Int,
        ) = PermissionGroup
            .newBuilder()
            .setPermissionGroupId(id)
            .setWeight(weight)
            .build()

        val names = listOf(group("admin", 900), group("vip", 300), group("default", 100)).map(Tablist::teamName)
        names shouldBeSortedWith naturalOrder()
        names.forEach { it shouldHaveMaxLength 16 }
        Tablist.teamName(group("moderator-senior", 500)) shouldNotBe Tablist.teamName(group("moderator-senior-2", 500))
    }
}
