package io.ogwars.cloud.apitest

import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import java.time.Instant
import java.util.UUID

/** Stores a passkey the way Spring's registration does, for tests that need one but no browser. */
class TestPasskeys(
    private val entities: PublicKeyCredentialUserEntityRepository,
    private val credentials: UserCredentialRepository,
) {
    fun register(userId: UUID) {
        val entity =
            ImmutablePublicKeyCredentialUserEntity
                .builder()
                .name(userId.toString())
                .id(Bytes.random())
                .displayName(userId.toString())
                .build()
        entities.save(entity)
        credentials.save(
            ImmutableCredentialRecord
                .builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.random())
                .userEntityUserId(entity.id)
                .publicKey(ImmutablePublicKeyCose(Bytes.random().bytes))
                .signatureCount(0)
                .uvInitialized(true)
                .transports(emptySet())
                .backupEligible(false)
                .backupState(false)
                .attestationObject(Bytes.random())
                .attestationClientDataJSON(Bytes.random())
                .created(Instant.now())
                .lastUsed(Instant.now())
                .label("test key")
                .build(),
        )
    }
}
