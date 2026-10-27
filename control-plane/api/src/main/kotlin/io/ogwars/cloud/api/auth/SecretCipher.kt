package io.ogwars.cloud.api.auth

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A secret encrypted under the key named [keyId]: the nonce followed by the AES-GCM ciphertext and tag. */
class SealedSecret(
    val keyId: String,
    val bytes: ByteArray,
)

/**
 * AES-256-GCM for secrets the API must read back, such as TOTP seeds (D-0097). The caller's context, e.g. the
 * user id, is authenticated with the data, so a row copied to another user does not decrypt.
 */
class SecretCipher(
    private val keys: Map<String, SecretKey>,
    private val activeKeyId: String,
    private val random: SecureRandom,
) {
    init {
        require(activeKeyId in keys) { "The active key '$activeKeyId' is not among ${keys.keys}" }
    }

    fun seal(
        plain: ByteArray,
        context: ByteArray,
    ): SealedSecret {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, keys.getValue(activeKeyId), nonce, context)
        return SealedSecret(activeKeyId, nonce + cipher.doFinal(plain))
    }

    fun open(
        sealed: SealedSecret,
        context: ByteArray,
    ): ByteArray {
        val key = keys[sealed.keyId] ?: throw IllegalStateException("Key '${sealed.keyId}' is no longer configured")
        val nonce = sealed.bytes.copyOfRange(0, NONCE_BYTES)
        return cipher(Cipher.DECRYPT_MODE, key, nonce, context).doFinal(
            sealed.bytes,
            NONCE_BYTES,
            sealed.bytes.size - NONCE_BYTES,
        )
    }

    private fun cipher(
        mode: Int,
        key: SecretKey,
        nonce: ByteArray,
        context: ByteArray,
    ): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(context)
        }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_BYTES = 32

        fun of(
            totp: AuthProperties.Totp,
            random: SecureRandom,
        ): SecretCipher =
            SecretCipher(
                totp.keys.mapValues { (id, encoded) ->
                    val bytes = Base64.getDecoder().decode(encoded.trim())
                    require(bytes.size == KEY_BYTES) { "Key '$id' must be $KEY_BYTES bytes (AES-256)" }
                    SecretKeySpec(bytes, "AES")
                },
                totp.activeKeyId,
                random,
            )
    }
}
