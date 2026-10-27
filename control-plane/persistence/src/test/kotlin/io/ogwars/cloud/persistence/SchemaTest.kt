package io.ogwars.cloud.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

@PersistenceTest
class SchemaTest(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val probe: SchemaVersionProbe,
) {
    @Test
    fun `the probe reports the migrated schema as current`() {
        probe.requiredVersion shouldBe LATEST_MIGRATION
        probe.appliedVersion() shouldBe LATEST_MIGRATION
        probe.isCurrent() shouldBe true
    }

    @Test
    fun `seeds the network settings and the default permission group`() {
        jdbc.queryForObject("select count(*) from network_settings", Int::class.java) shouldBe 1
        jdbc.queryForObject("select id from permission_groups where is_default", String::class.java) shouldBe "default"
    }

    @Test
    fun `allows only one default permission group`() {
        shouldThrow<DataIntegrityViolationException> {
            jdbc.update("insert into permission_groups (id, name, weight, is_default) values ('vip', 'VIP', 10, true)")
        }
    }

    @Test
    fun `rejects group ids that would break pod names`() {
        shouldThrow<DataIntegrityViolationException> { insertGroup("Bad_Name", "DYNAMIC", maxInstances = 2) }
    }

    @Test
    fun `rejects static groups with more than one server`() {
        shouldThrow<DataIntegrityViolationException> { insertGroup("smp", "STATIC", maxInstances = 2) }
    }

    @Test
    fun `accepts a static group with one server`() {
        insertGroup("smp", "STATIC", maxInstances = 1) shouldBe 1
    }

    @Test
    fun `the controller role may change data but not the schema`() {
        val privileges =
            jdbc.queryForList(
                """
                select privilege_type from information_schema.role_table_grants
                where grantee = 'ogcloud_controller' and table_name = 'groups'
                """.trimIndent(),
                String::class.java,
            )

        privileges.toSet() shouldBe setOf("SELECT", "INSERT", "UPDATE", "DELETE")
    }

    private fun insertGroup(
        id: String,
        type: String,
        maxInstances: Int,
    ) = jdbc.update(
        """
        insert into groups (id, type, profile_id, min_online, max_instances, players_per_server,
            scale_up_threshold, scale_down_threshold, cooldown_seconds, drain_timeout_seconds,
            memory_request, memory_limit, cpu_request)
        values (?, ?, 'paper-26.3', 1, ?, 50, 0.8, 0.3, 60, 60, '1Gi', '2Gi', '500m')
        """.trimIndent(),
        id,
        type,
        maxInstances,
    )
}

/** Bump with every new migration file. */
private const val LATEST_MIGRATION = 4
