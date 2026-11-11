package io.ogwars.cloud.domain.text

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.permission.RankDisplay
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

/** Stored in-game texts must be valid tag texts with only their field's placeholders (D-0200). */
class TextsTest {
    @Test
    fun `a rank's texts are refused with the field and where the error is`() {
        RankDisplay(chatPrefix = "<gradient:#ff0000:#0000ff>[VIP]</gradient> ", nameFormat = "<gold>{name}")
        shouldThrow<IllegalArgumentException> { RankDisplay(chatPrefix = "<frog>VIP") }.message shouldBe
            "chatPrefix: unknown tag <frog> (at character 0)"
        shouldThrow<IllegalArgumentException> { RankDisplay(nameFormat = "<red>") }.message shouldBe
            "nameFormat: this text must show {name} (at character 0)"
    }

    @Test
    fun `network settings refuse placeholders their texts do not offer`() {
        settings(tablistHeader = "{onlinePlayers}/{maxPlayers}")
        shouldThrow<IllegalArgumentException> { settings(tablistHeader = "{players}") }.message shouldBe
            "tablistHeader: {players} is no placeholder here; this text offers {server}, {group}, {proxy}, " +
            "{onlinePlayers}, {maxPlayers}, {ping}, {permissionGroup} (at character 0)"
        shouldThrow<IllegalArgumentException> { settings(motd = "a < b") }.message shouldBe
            "motd: '<' starts no tag; write \\< for a literal < (at character 2)"
    }

    @Test
    fun `an NPC's title takes its group's live counts`() {
        npc(title = "<yellow>{players} playing")
        shouldThrow<IllegalArgumentException> { npc(title = "</bold>") }.message shouldBe
            "title: </bold> closes no open tag (at character 0)"
    }

    private fun settings(
        motd: String = "<green>An OgCloud network",
        tablistHeader: String = "",
    ) = NetworkSettings(
        maintenance = false,
        motd = motd,
        maintenanceMotd = "",
        versionName = "OgCloud",
        maintenanceVersionName = "",
        maxPlayers = 100,
        defaultGroupId = GroupId("lobby"),
        routingStrategy = RoutingStrategy.LEAST_FILLED,
        permissionSystemEnabled = true,
        tablistEnabled = true,
        tablistHeader = tablistHeader,
        tablistFooter = "",
        maintenanceKickMessage = "",
        eulaAcceptedAt = null,
        requireMinecraftLink = false,
    )

    private fun npc(title: String) =
        Npc(UUID.randomUUID(), GroupId("lobby"), Location("world", 0.0, 0.0, 0.0), title = title)
}
