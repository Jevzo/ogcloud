package io.ogwars.cloud.api.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/** RS256 keys: [activeKeyId] signs and every key verifies, so rotation is add, activate, remove (D-0090). */
class SigningKeys(
    val keys: List<RSAKey>,
    val activeKeyId: String,
) {
    init {
        require(
            keys.any {
                it.keyID == activeKeyId
            },
        ) { "Active signing key '$activeKeyId' is not among ${keys.map { it.keyID }}" }
    }

    fun jwkSource(): JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(keys.toList()))

    companion object {
        private const val PEM_SUFFIX = ".pem"
        private const val PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----"
        private const val MIN_KEY_BITS = 2048

        /** Reads every `<kid>.pem` in [dir]; Kubernetes' `..data` entries are skipped. */
        fun load(
            dir: Path,
            activeKeyId: String,
        ): SigningKeys {
            val files =
                Files.list(dir).use { paths ->
                    paths
                        .filter {
                            it.fileName.toString().endsWith(
                                PEM_SUFFIX,
                            ) && Files.isRegularFile(it)
                        }.sorted()
                        .toList()
                }
            require(files.isNotEmpty()) { "No signing keys (*$PEM_SUFFIX) in $dir" }
            return SigningKeys(
                files.map {
                    parse(it.fileName.toString().removeSuffix(PEM_SUFFIX), Files.readString(it))
                },
                activeKeyId,
            )
        }

        fun parse(
            keyId: String,
            pem: String,
        ): RSAKey {
            require(pem.contains(PKCS8_HEADER)) { "Signing key '$keyId' must be a PKCS#8 PEM ($PKCS8_HEADER)" }
            val der = Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))
            val rsa = KeyFactory.getInstance("RSA")
            val private =
                rsa.generatePrivate(PKCS8EncodedKeySpec(der)) as? RSAPrivateCrtKey
                    ?: throw IllegalArgumentException(
                        "Signing key '$keyId' lacks the CRT parameters to derive its public key",
                    )
            require(
                private.modulus.bitLength() >= MIN_KEY_BITS,
            ) { "Signing key '$keyId' must have at least $MIN_KEY_BITS bits" }
            val public = rsa.generatePublic(RSAPublicKeySpec(private.modulus, private.publicExponent)) as RSAPublicKey
            return RSAKey
                .Builder(public)
                .privateKey(private)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build()
        }
    }
}
