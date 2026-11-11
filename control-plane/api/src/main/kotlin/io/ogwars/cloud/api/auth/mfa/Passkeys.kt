package io.ogwars.cloud.api.auth.mfa

import org.springframework.security.web.webauthn.api.CredentialRecord
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

const val MAX_PASSKEY_LABEL = 64

/** A passkey's name as the user gave it at registration or rename: trimmed, 1 to [MAX_PASSKEY_LABEL] characters. */
fun passkeyLabel(raw: String): String =
    raw.trim().also {
        require(it.length in 1..MAX_PASSKEY_LABEL) { "A passkey's name has 1 to $MAX_PASSKEY_LABEL characters" }
    }

/**
 * Spring's credential store, which checks a new passkey's name as a rename does. Sign-ins save existing passkeys too;
 * those pass unchecked, so a name stored before the rule never blocks a sign-in.
 */
class LabelledCredentials(
    private val delegate: UserCredentialRepository,
) : UserCredentialRepository by delegate {
    override fun save(record: CredentialRecord) {
        val fresh = delegate.findByCredentialId(record.credentialId) == null
        delegate.save(
            if (fresh) {
                ImmutableCredentialRecord.fromCredentialRecord(record).label(passkeyLabel(record.label)).build()
            } else {
                record
            },
        )
    }
}

/** A passkey as the API shows it; the id is its credential id in base64url. */
data class PasskeyView(
    val id: String,
    val label: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
) {
    companion object {
        fun of(record: CredentialRecord) =
            PasskeyView(record.credentialId.toBase64UrlString(), record.label, record.created, record.lastUsed)
    }
}

/** A user's registered passkeys (D-0085), stored by Spring under the principal name, which is the user's id. */
@Component
class Passkeys(
    private val userEntities: PublicKeyCredentialUserEntityRepository,
    private val credentials: UserCredentialRepository,
) {
    fun exist(userId: UUID): Boolean =
        userEntities.findByUsername(userId.toString())?.let { credentials.findByUserId(it.id).isNotEmpty() } == true

    fun list(userId: UUID): List<CredentialRecord> =
        userEntities.findByUsername(userId.toString())?.let { credentials.findByUserId(it.id) }.orEmpty()

    /** One of the user's passkeys by its credential id, as the API shows it (base64url). */
    fun find(
        userId: UUID,
        id: String,
    ): CredentialRecord =
        list(userId).find { it.credentialId.toBase64UrlString() == id }
            ?: throw NoSuchElementException("No such passkey")

    fun rename(
        record: CredentialRecord,
        label: String,
    ) = credentials.save(ImmutableCredentialRecord.fromCredentialRecord(record).label(label).build())

    fun remove(record: CredentialRecord) = credentials.delete(record.credentialId)

    /** Credentials go with their user entity (V2 cascades). */
    fun deleteAll(userId: UUID) {
        userEntities.findByUsername(userId.toString())?.let { userEntities.delete(it.id) }
    }
}
