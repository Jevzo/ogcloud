package io.ogwars.cloud.domain.text

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.networkSettings
import io.ogwars.cloud.domain.npc.Location
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.domain.permission.RankDisplay
import io.ogwars.cloud.text.TextErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

/** Stored in-game texts are checked when written, never when read, with only their field's placeholders (D-0205). */
class TextsTest {
    @Test
    fun `invalid texts still load, so a bad row never breaks a read`() {
        RankDisplay(chatPrefix = "<frog>VIP")
        networkSettings(motd = "a < b")
        npc(title = "</bold>")
    }

    @Test
    fun `a rank's texts are refused with the field and where the error is`() {
        RankDisplay(chatPrefix = "<gradient:#ff0000:#0000ff>[VIP]</gradient> ", nameFormat = "<gold>{name}")
            .requireValidTexts()
        val refused =
            shouldThrow<InvalidTextException> {
                RankDisplay(chatPrefix = "<frog>VIP", nameFormat = "<red>").requireValidTexts()
            }
        refused.message shouldBe
            "chatPrefix: unknown tag <frog> (at character 0); nameFormat: this text must show {name} (at character 5)"
        refused.errors.mapValues { (_, errors) -> errors.map { it.code } } shouldBe
            mapOf(
                "chatPrefix" to listOf(TextErrorCode.UNKNOWN_TAG),
                "nameFormat" to listOf(TextErrorCode.MISSING_PLACEHOLDER),
            )
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
        delimiter = '|',
        textBlock = """
        motd                   | {server}      | false
        maintenanceMotd        | <red>Closed   | true
        versionName            | {ping}        | false
        maintenanceVersionName | §cOld         | false
        tablistHeader          | {ping}        | true
        tablistFooter          | {ping}        | true
        tablistFooter          | {players}     | false
        maintenanceKickMessage | {player}      | false
        maintenanceKickMessage | <red>Later    | true""",
    )
    fun `every network text is checked against its own field`(
        label: String,
        text: String,
        valid: Boolean,
    ) {
        val settings =
            when (label) {
                "motd" -> networkSettings(motd = text)
                "maintenanceMotd" -> networkSettings(maintenanceMotd = text)
                "versionName" -> networkSettings(versionName = text)
                "maintenanceVersionName" -> networkSettings(maintenanceVersionName = text)
                "tablistHeader" -> networkSettings(tablistHeader = text)
                "tablistFooter" -> networkSettings(tablistFooter = text)
                else -> networkSettings(maintenanceKickMessage = text)
            }
        if (valid) {
            settings.requireValidTexts()
        } else {
            shouldThrow<InvalidTextException> { settings.requireValidTexts() }.errors.keys shouldBe setOf(label)
        }
    }

    @Test
    fun `an NPC's title and subtitle take its group's live counts`() {
        npc(title = "<yellow>{players} playing", subtitle = "{servers} servers").requireValidTexts()
        shouldThrow<InvalidTextException> { npc(subtitle = "{name}").requireValidTexts() }.errors.keys shouldBe
            setOf("subtitle")
        shouldThrow<InvalidTextException> { npc(title = "</bold>").requireValidTexts() }.message shouldBe
            "title: </bold> closes no open tag (at character 0)"
    }

    private fun npc(
        title: String = "",
        subtitle: String = "",
    ) = Npc(UUID.randomUUID(), GroupId("lobby"), Location("world", 0.0, 0.0, 0.0), title = title, subtitle = subtitle)
}
