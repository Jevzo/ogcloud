package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.store.Revisioned
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The load balancer parses no tags, so the controller renders the server list's texts for it (D-0202, D-0204). */
class ReadModelMessagesTest {
    @Test
    fun `network settings carry their server-list texts rendered for new and old clients`() {
        val settings =
            NetworkSettings(
                maintenance = false,
                motd = "<gradient:#ff0000:#0000ff>OgW</gradient> <gray>\"net\"",
                maintenanceMotd = "<red>Closed",
                versionName = "<gold>OgWars",
                maintenanceVersionName = "<#aa0000>Maintenance",
                maxPlayers = 100,
                defaultGroupId = GroupId("lobby"),
                routingStrategy = RoutingStrategy.LEAST_FILLED,
                permissionSystemEnabled = true,
                tablistEnabled = false,
                tablistHeader = "",
                tablistFooter = "",
                maintenanceKickMessage = "",
                eulaAcceptedAt = null,
                requireMinecraftLink = false,
            )

        val texts = ReadModelMessages.networkSettings(Revisioned(settings, 3)).serverList

        texts.motdJson shouldBe
            """{"text":"","extra":[{"text":"O","color":"#ff0000"},{"text":"g","color":"#800080"},""" +
            """{"text":"W","color":"#0000ff"},{"text":" "},{"text":"\"net\"","color":"gray"}]}"""
        texts.motdLegacy shouldBe "§4O§5g§1W§r §7\"net\""
        texts.maintenanceMotdJson shouldBe """{"text":"","extra":[{"text":"Closed","color":"red"}]}"""
        texts.maintenanceMotdLegacy shouldBe "§cClosed"
        texts.versionNameLegacy shouldBe "§6OgWars"
        texts.maintenanceVersionNameLegacy shouldBe "§4Maintenance"
    }
}
