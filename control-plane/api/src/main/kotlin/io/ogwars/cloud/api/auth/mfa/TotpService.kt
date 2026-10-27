package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.api.auth.AuthProperties
import io.ogwars.cloud.api.auth.SealedSecret
import io.ogwars.cloud.api.auth.SecretCipher
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.StoredTotp
import io.ogwars.cloud.domain.store.TotpStore
import io.ogwars.cloud.domain.user.Base32
import io.ogwars.cloud.domain.user.Totp
import io.ogwars.cloud.domain.user.WebUser
import io.ogwars.cloud.service.SecretGenerator
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.time.Clock
import java.util.Base64
import java.util.UUID

/** What an authenticator app needs: the secret, and the same as an `otpauth://` link. */
data class TotpEnrollment(
    val secret: String,
    val uri: String,
)

/** Authenticator-app codes (D-0084), with secrets encrypted at rest (D-0097). */
@Service
class TotpService(
    private val totps: TotpStore,
    private val passkeys: Passkeys,
    private val cipher: SecretCipher,
    private val secrets: SecretGenerator,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    private val totp = Totp()

    fun isEnrolled(userId: UUID): Boolean = totps.find(userId)?.confirmedAt != null

    /** The pending enrollment if there is one, so a mistyped first code does not change the secret. */
    fun pendingOrStart(user: WebUser): TotpEnrollment {
        val pending = totps.find(user.id)?.takeIf { it.confirmedAt == null } ?: return startEnrollment(user)
        return enrollment(user.username, open(pending))
    }

    fun startEnrollment(user: WebUser): TotpEnrollment {
        val secret = Base64.getUrlDecoder().decode(secrets.next())
        val sealed = cipher.seal(secret, context(user.id))
        if (!totps.startEnrollment(StoredTotp(user.id, sealed.keyId, sealed.bytes))) {
            throw ConflictException("Remove the current authenticator before adding another")
        }
        return enrollment(user.username, secret)
    }

    fun confirm(
        userId: UUID,
        code: String,
    ) {
        val pending =
            totps.find(userId)?.takeIf { it.confirmedAt == null }
                ?: throw IllegalArgumentException("Nothing to confirm")
        val now = clock.instant()
        val step =
            totp.verify(open(pending), code, now, lastUsedStep = null) ?: throw IllegalArgumentException("Wrong code")
        if (!totps.confirm(userId, now, step)) throw ConflictException("The authenticator was confirmed meanwhile")
    }

    /** True once per valid code: a code used before is refused. */
    fun verify(
        userId: UUID,
        code: String,
    ): Boolean {
        val enrolled = totps.find(userId)?.takeIf { it.confirmedAt != null } ?: return false
        val step = totp.verify(open(enrolled), code, clock.instant(), enrolled.lastUsedStep) ?: return false
        return totps.markUsed(userId, step)
    }

    /** Needs a current code (D-0108); an owner keeps it unless a passkey stands in (D-0097). */
    fun remove(
        user: WebUser,
        code: String,
    ) {
        if (user.isOwner && !passkeys.exist(user.id)) {
            throw ConflictException("Owners need a second factor; add a passkey before removing the authenticator")
        }
        require(verify(user.id, code)) { "Wrong code" }
        totps.delete(user.id)
    }

    private fun enrollment(
        username: String,
        secret: ByteArray,
    ): TotpEnrollment {
        val encoded = Base32.encode(secret)
        return TotpEnrollment(encoded, uri(username, encoded))
    }

    private fun open(stored: StoredTotp) =
        cipher.open(SealedSecret(stored.keyId, stored.encryptedSecret), context(stored.userId))

    private fun context(userId: UUID) = userId.toString().toByteArray()

    private fun uri(
        username: String,
        secret: String,
    ): String {
        val issuer = properties.issuer.host
        val label = URLEncoder.encode("$issuer:$username", Charsets.UTF_8).replace("+", "%20")
        val query =
            "secret=$secret&issuer=${URLEncoder.encode(issuer, Charsets.UTF_8)}&algorithm=SHA1&digits=${Totp.DIGITS}" +
                "&period=${Totp.STEP_SECONDS}"
        return "otpauth://totp/$label?$query"
    }
}
