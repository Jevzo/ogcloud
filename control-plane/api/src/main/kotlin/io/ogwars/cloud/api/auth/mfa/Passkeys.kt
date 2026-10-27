package io.ogwars.cloud.api.auth.mfa

import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.stereotype.Component
import java.util.UUID

/** A user's registered passkeys (D-0085), stored by Spring under the principal name, which is the user's id. */
@Component
class Passkeys(
    private val userEntities: PublicKeyCredentialUserEntityRepository,
    private val credentials: UserCredentialRepository,
) {
    fun exist(userId: UUID): Boolean =
        userEntities.findByUsername(userId.toString())?.let { credentials.findByUserId(it.id).isNotEmpty() } == true

    /** Credentials go with their user entity (V2 cascades). */
    fun deleteAll(userId: UUID) {
        userEntities.findByUsername(userId.toString())?.let { userEntities.delete(it.id) }
    }
}
