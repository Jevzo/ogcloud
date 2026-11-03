package io.ogwars.cloud.node.core.format

import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.node.core.permission.Rank
import io.ogwars.cloud.node.core.text.ColorCodes
import io.ogwars.cloud.node.core.text.Messages
import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveMaxLength
import org.junit.jupiter.api.Test

class FormatsTest {
    private val chat = ChatFormat(Messages.load())

    private fun rank(group: PermissionGroup) = Rank(PlayerProfile.getDefaultInstance(), group)

    @Test
    fun `chat heads use the group's display and fill blanks from the catalog`() {
        val admin =
            PermissionGroup
                .newBuilder()
                .setChatPrefix("&c[Admin] ")
                .setNameColor("&c")
                .setChatSuffix(" &8» &f")
                .build()

        chat.head(rank(admin), "Jevzo") shouldBe "&c[Admin] &cJevzo &8» &f"
        chat.head(rank(PermissionGroup.getDefaultInstance()), "Steve") shouldBe "&7Steve&7: &f"
        chat.head(null, "Steve") shouldBe "&7Steve&7: &f"
    }

    @Test
    fun `the last colour of a text`() {
        ColorCodes.lastColor("&c[Admin] &cJevzo &8» &F") shouldBe 'f'
        ColorCodes.lastColor("&lbold only") shouldBe null
    }

    @Test
    fun `tablist placeholders are v1's and filled once`() {
        val values = TablistValues("lobby-789abc", "lobby", "proxy-1", 12, 1000, 42, "%server%")

        Tablist.fill("&7%server% %onlinePlayers%/%maxPlayers% %ping%ms %permissionGroup% %unknown%", values) shouldBe
            "&7lobby-789abc 12/1000 42ms %server% %unknown%"
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
