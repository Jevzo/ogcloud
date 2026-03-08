package io.ogwars.cloud.api.util

object EmailAddressNormalizer {
    fun normalize(email: String): String {
        val normalized = email.trim().lowercase()

        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Email must not be blank")
        }

        return normalized
    }
}
