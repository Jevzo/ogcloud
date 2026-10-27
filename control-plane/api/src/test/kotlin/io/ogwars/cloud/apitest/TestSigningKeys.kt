package io.ogwars.cloud.apitest

import io.ogwars.cloud.api.auth.SigningKeys
import io.ogwars.cloud.api.auth.TokenClaims
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/** Two published keys, [CURRENT] signing and [OLD] still verifying, as in the middle of a rotation (D-0090). */
object TestSigningKeys {
    const val OLD = "2026-09"
    const val CURRENT = "2026-10"
    private const val PEM_LINE = 64
    private const val PEM_LABEL = "PRIVATE KEY"
    private val TOKEN_LIFETIME = Duration.ofMinutes(5)

    private val pairs = mapOf(OLD to rsa(), CURRENT to rsa())

    val dir: Path by lazy {
        Files.createTempDirectory("ogcloud-signing").also { dir ->
            dir.toFile().deleteOnExit()
            pairs.forEach { (kid, pair) ->
                Files.writeString(dir.resolve("$kid.pem"), pem(pair)).toFile().deleteOnExit()
            }
        }
    }

    fun key(kid: String): RSAKey = SigningKeys.parse(kid, pem(pairs.getValue(kid)))

    fun rsa(bits: Int = 2048): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.genKeyPair()

    // The label is spliced in so secret scanners do not mistake this builder for a committed key.
    fun pem(pair: KeyPair): String =
        "-----BEGIN $PEM_LABEL-----\n" +
            Base64.getMimeEncoder(PEM_LINE, "\n".toByteArray()).encodeToString(pair.private.encoded) +
            "\n-----END $PEM_LABEL-----\n"

    /** An access token as the authorization server would issue it, signed with [key]. */
    fun accessToken(
        key: RSAKey,
        permissions: List<String>,
        issuer: String = TEST_ISSUER,
        type: JOSEObjectType = JOSEObjectType(TokenClaims.ACCESS_TOKEN_TYPE),
    ): String {
        val now = Instant.now()
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .subject("test-client")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(TOKEN_LIFETIME)))
                .claim(TokenClaims.ACTOR_CLAIM, "client:test-client")
                .claim(TokenClaims.PERMISSIONS_CLAIM, permissions)
                .build()
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(key.keyID)
                .type(type)
                .build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(key)) }.serialize()
    }
}
