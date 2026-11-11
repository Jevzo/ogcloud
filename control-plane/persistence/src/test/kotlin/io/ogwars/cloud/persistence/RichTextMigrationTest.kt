package io.ogwars.cloud.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.postgresql.PostgreSQLContainer

/** V5 turns stored `&` texts into tags (D-0201) and name colours into name formats (D-0203). */
class RichTextMigrationTest {
    @Test
    fun `stored texts keep how they looked, as tags`() {
        val database =
            PostgreSQLContainer(
                PostgresContainerConfiguration.POSTGRES_IMAGE,
            ).withInitScript("db/test-init.sql")
        database.use { container ->
            container.start()
            val dataSource = container.dataSource()
            val jdbc = JdbcClient.create(dataSource)
            migrate(dataSource, target = "4")
            jdbc
                .sql(
                    """
                    update network_settings set tablist_header = '&7%onlinePlayers%/%maxPlayers% &cI <3 {you}',
                        tablist_footer = '%ping% ms on %server%', version_name = 'a\b'
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
                    "<green>An OgCloud network",
                    "<red>Maintenance",
                    "<red>The network is in maintenance.",
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
                    listOf("<gold>[<bold>VIP<reset><gold>] ", "", "<red>{name}", "<bold><reset><gold>VIP "),
                )
            jdbc
                .sql("select title, subtitle from npcs")
                .query { row, _ -> listOf(row.getString(1), row.getString(2)) }
                .single() shouldBe listOf("<yellow>BedWars", "<gray>{players} players on {servers}")
        }
    }
}
