package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.store.StoredTotp
import io.ogwars.cloud.domain.store.TotpStore
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JdbcTotpStore(
    private val jdbc: JdbcClient,
) : TotpStore {
    override fun find(userId: UUID): StoredTotp? =
        jdbc
            .sql("select * from web_user_totp where user_id = :userId")
            .param("userId", userId)
            .query(TOTP)
            .optional()
            .orElse(null)

    override fun startEnrollment(totp: StoredTotp): Boolean =
        jdbc
            .sql(
                """
                insert into web_user_totp (user_id, key_id, secret_encrypted) values (:userId, :keyId, :secret)
                on conflict (user_id) do update set key_id = excluded.key_id, secret_encrypted = excluded.secret_encrypted,
                    last_step = null
                where web_user_totp.confirmed_at is null
                """.trimIndent(),
            ).param("userId", totp.userId)
            .param("keyId", totp.keyId)
            .param("secret", totp.encryptedSecret)
            .update() == 1

    override fun confirm(
        userId: UUID,
        at: Instant,
        step: Long,
    ): Boolean =
        jdbc
            .sql(
                "update web_user_totp set confirmed_at = :at, last_step = :step where user_id = :userId and confirmed_at is null",
            ).param("userId", userId)
            .param("at", at.toTimestamp())
            .param("step", step)
            .update() == 1

    // The conditional update makes two requests with the same code race safely: only one wins.
    override fun markUsed(
        userId: UUID,
        step: Long,
    ): Boolean =
        jdbc
            .sql(
                """
                update web_user_totp set last_step = :step
                where user_id = :userId and confirmed_at is not null and (last_step is null or last_step < :step)
                """.trimIndent(),
            ).param("userId", userId)
            .param("step", step)
            .update() == 1

    override fun delete(userId: UUID) {
        jdbc.sql("delete from web_user_totp where user_id = :userId").param("userId", userId).update()
    }

    private companion object {
        val TOTP =
            RowMapper { rs, _ ->
                StoredTotp(
                    userId = rs.uuid("user_id"),
                    keyId = rs.getString("key_id"),
                    encryptedSecret = rs.getBytes("secret_encrypted"),
                    confirmedAt = rs.instantOrNull("confirmed_at"),
                    lastUsedStep = rs.getLong("last_step").takeUnless { rs.wasNull() },
                )
            }
    }
}
