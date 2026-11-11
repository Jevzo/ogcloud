package io.ogwars.cloud.persistence

import io.ogwars.cloud.text.TextField
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient

/** V5 turns stored `&` texts into tags (D-0201) and name colours into name formats (D-0203). */
class RichTextMigrationTest {
    @Test
    fun `stored texts keep how they looked, as tags`() {
        PostgresContainerConfiguration.postgresContainer().use { container ->
            container.start()
            val dataSource = container.dataSource()
            val jdbc = JdbcClient.create(dataSource)
            migrate(dataSource, target = "4")
            jdbc
                .sql(
                    """
                    update network_settings set tablist_header = '&7%onlinePlayers%/%maxPlayers% &cI <3 {you}',
                        tablist_footer = '%ping% ms on %server%', version_name = 'a\b', motd = '&l<reset>&cX',
                        maintenance_motd = '&L&R', maintenance_kick_message = '&c&r&cX'
                    """.trimIndent(),
                ).update()
            jdbc
                .sql(
                    """
                    insert into permission_groups (id, name, weight, chat_prefix, chat_suffix, name_color, tab_prefix)
                    values ('vip', 'VIP', 10, '&6[&lVIP&6] ', '&r', '&C', '&l&6VIP ')
                    """.trimIndent(),
                ).update()
            jdbc
                .sql(
                    """
                    insert into groups (id, type, profile_id, min_online, max_instances, players_per_server,
                        scale_up_threshold, scale_down_threshold, cooldown_seconds, drain_timeout_seconds,
                        memory_request, memory_limit, cpu_request)
                    values ('lobby', 'DYNAMIC', 'paper-26.3', 1, 2, 50, 0.8, 0.3, 60, 60, '2Gi', '2Gi', '500m')
                    """.trimIndent(),
                ).update()
            jdbc
                .sql(
                    """
                    insert into npcs (id, group_id, world, x, y, z, title, subtitle)
                    values (gen_random_uuid(), 'lobby', 'world', 0, 0, 0, '&eBedWars', '&7%players% players on %servers%')
                    """.trimIndent(),
                ).update()

            migrate(dataSource)

            jdbc
                .sql(
                    """
                    select motd, maintenance_motd, maintenance_kick_message, tablist_header, tablist_footer, version_name
                    from network_settings
                    """.trimIndent(),
                ).query { row, _ -> (1..6).map(row::getString) }
                .single() shouldBe
                listOf(
                    // A literal <reset> stays text, and the reset after it still ends the bold.
                    "<bold>\\<reset><reset><red>X",
                    "<bold><reset>",
                    "<red><reset><red>X",
                    "<gray>{onlinePlayers}/{maxPlayers} <reset><red>I \\<3 \\{you}",
                    "{ping} ms on {server}",
                    "a\\\\b",
                )
            jdbc
                .sql(
                    "select chat_prefix, chat_suffix, name_format, tab_prefix from permission_groups order by id",
                ).query { row, _ -> (1..4).map(row::getString) }
                .list() shouldBe
                listOf(
                    listOf("", "", "", ""),
                    // A suffix of only codes stays non-empty, so it still replaces the catalog's separator.
                    listOf("<gold>[<bold>VIP<reset><gold>] ", "<reset>", "<red>{name}", "<bold><reset><gold>VIP "),
                )
            jdbc
                .sql("select title, subtitle from npcs")
                .query { row, _ -> listOf(row.getString(1), row.getString(2)) }
                .single() shouldBe listOf("<yellow>BedWars", "<gray>{players} players on {servers}")
            migratedTextsAreValid(jdbc)
        }
    }

    /** Every migrated text is one the API would store in its field (D-0205). */
    private fun migratedTextsAreValid(jdbc: JdbcClient) {
        val fields =
            mapOf(
                "network_settings" to
                    mapOf(
                        "motd" to TextField.MOTD,
                        "maintenance_motd" to TextField.MAINTENANCE_MOTD,
                        "version_name" to TextField.VERSION_NAME,
                        "maintenance_version_name" to TextField.MAINTENANCE_VERSION_NAME,
                        "tablist_header" to TextField.TABLIST_HEADER,
                        "tablist_footer" to TextField.TABLIST_FOOTER,
                        "maintenance_kick_message" to TextField.MAINTENANCE_KICK_MESSAGE,
                    ),
                "permission_groups" to
                    mapOf(
                        "chat_prefix" to TextField.CHAT_PREFIX,
                        "chat_suffix" to TextField.CHAT_SUFFIX,
                        "name_format" to TextField.NAME_FORMAT,
                        "tab_prefix" to TextField.TAB_PREFIX,
                    ),
                "npcs" to mapOf("title" to TextField.NPC_TITLE, "subtitle" to TextField.NPC_SUBTITLE),
            )
        fields.forEach { (table, columns) ->
            columns.forEach { (column, field) ->
                jdbc.sql("select $column from $table").query { row, _ -> row.getString(1).orEmpty() }.list().forEach {
                    withClue("$table.$column \"$it\"") { field.validate(it).shouldBeEmpty() }
                }
            }
        }
    }
}
