package io.ogwars.cloud.api.auth

import io.ogwars.cloud.apitest.ApiTest
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAnyOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@ApiTest
class AuthorizationCleanupTest(
    @Autowired private val cleanup: AuthorizationCleanup,
    @Autowired private val jdbc: JdbcClient,
) {
    private val now = Instant.parse("2030-01-01T00:00:00Z")

    @Test
    fun `only authorizations whose every token expired are deleted`() {
        insert("expired-access", access = now.minusSeconds(1))
        insert("expired-both", access = now.minus(Duration.ofDays(1)), refresh = now.minusSeconds(1))
        insert("unused-code", code = now.minusSeconds(1))
        insert("live-refresh", access = now.minus(Duration.ofDays(1)), refresh = now.plus(Duration.ofDays(1)))
        insert("live-access", access = now.plusSeconds(60))

        cleanup.deleteExpired(now)

        val left = jdbc.sql("select id from oauth2_authorization").query(String::class.java).list()
        left shouldContainAll listOf("live-refresh", "live-access")
        left shouldNotContainAnyOf listOf("expired-access", "expired-both", "unused-code")
    }

    private fun insert(
        id: String,
        code: Instant? = null,
        access: Instant? = null,
        refresh: Instant? = null,
    ) {
        jdbc
            .sql(
                """
                insert into oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,
                    authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at)
                values (:id, 'ogcloud-web', 'someone', 'authorization_code', :code, :access, :refresh)
                """.trimIndent(),
            ).param("id", id)
            .param("code", code?.let(Timestamp::from))
            .param("access", access?.let(Timestamp::from))
            .param("refresh", refresh?.let(Timestamp::from))
            .update()
    }
}
