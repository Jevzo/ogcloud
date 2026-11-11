package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.domain.network.networkSettings
import io.ogwars.cloud.domain.store.Revisioned
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The load balancer parses no tags, so the controller renders the server list's texts for it (D-0202, D-0204). */
class ReadModelMessagesTest {
    @Test
    fun `network settings carry their server-list texts rendered for new and old clients`() {
        val settings =
            networkSettings(
                motd = "<gradient:#ff0000:#0000ff>OgW</gradient> <gray>\"net\"",
                maintenanceMotd = "<red>Closed",
                versionName = "<gold>OgWars",
                maintenanceVersionName = "<#aa0000>Maintenance",
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

    @Test
    fun `a stored text from before a rule is shown as written instead of failing the read model`() {
        val texts =
            ReadModelMessages.networkSettings(Revisioned(networkSettings(motd = "I <3 OgCloud"), 1)).serverList

        texts.motdJson shouldBe """{"text":"","extra":[{"text":"I <3 OgCloud"}]}"""
        texts.motdLegacy shouldBe "I <3 OgCloud"
    }
}
