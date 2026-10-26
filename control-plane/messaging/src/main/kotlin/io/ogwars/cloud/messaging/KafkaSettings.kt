package io.ogwars.cloud.messaging

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.scram.ScramLoginModule

/** SCRAM credentials of the service's Strimzi KafkaUser; the password comes from Infisical (D-0044). */
data class ScramCredentials(
    val username: String,
    val password: String,
)

/** In-cluster TLS from the private CA (D-0041), as a PEM file mounted from a Secret. */
data class TlsTrust(
    val caCertificatePath: String,
)

data class KafkaSettings(
    val bootstrapServers: String,
    /** Service name; also the prefix of its consumer groups, matching the generated ACLs. */
    val clientId: String,
    val scram: ScramCredentials? = null,
    val tls: TlsTrust? = null,
) {
    /** Connection and security properties shared by producers, consumers and admin clients. */
    fun commonProperties(): Map<String, Any> =
        buildMap {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(CommonClientConfigs.CLIENT_ID_CONFIG, clientId)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol())
            scram?.let {
                put(SaslConfigs.SASL_MECHANISM, SCRAM_MECHANISM)
                put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig(it))
            }
            tls?.let {
                put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, PEM)
                put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, it.caCertificatePath)
            }
        }

    private fun securityProtocol(): String =
        when {
            scram != null && tls != null -> "SASL_SSL"
            scram != null -> "SASL_PLAINTEXT"
            tls != null -> "SSL"
            else -> "PLAINTEXT"
        }

    private fun jaasConfig(credentials: ScramCredentials): String =
        "${ScramLoginModule::class.java.name} required username=\"${credentials.username.escaped()}\" password=\"${credentials.password.escaped()}\";"

    private fun String.escaped() = replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        const val SCRAM_MECHANISM = "SCRAM-SHA-512"
        const val PEM = "PEM"
    }
}
