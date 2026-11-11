package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.common.REDACTED
import io.ogwars.cloud.domain.common.redacted
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.nio.file.Path
import java.time.Duration

/** `ogcloud.auth.*`: the built-in OIDC provider (D-0012, D-0090). */
@ConfigurationProperties("ogcloud.auth")
data class AuthProperties(
    /** The public origin, e.g. `https://cloud.example.com`; it is also the OIDC issuer (D-0098). */
    val issuer: URI,
    val signing: Signing,
    val totp: Totp,
    val tokens: Tokens = Tokens(),
    val clients: Clients = Clients(),
    /** Creates the first owner; bootstrap puts it into Infisical and it is useless once an owner exists (D-0094). */
    val setupToken: String? = null,
    /** The built login pages, a second entry of the web app (D-0212); the API image carries them. */
    val loginPages: Path? = null,
) {
    init {
        require(
            issuer.isAbsolute && issuer.path.isNullOrEmpty(),
        ) { "ogcloud.auth.issuer must be an origin without a path" }
    }

    override fun toString() =
        "AuthProperties(issuer=$issuer, signing=$signing, totp=$totp, tokens=$tokens, clients=$clients, " +
            "setupToken=${redacted(setupToken)}, loginPages=$loginPages)"

    data class Signing(
        /** Holds `<kid>.pem` RSA private keys (PKCS#8), synced from Infisical (D-0044). */
        val keysDir: Path,
        val activeKeyId: String,
    )

    data class Totp(
        /** Base64 AES-256 keys by id, synced from Infisical through the config tree (D-0097). */
        val keys: Map<String, String>,
        val activeKeyId: String,
    ) {
        override fun toString() = "Totp(keys=${keys.mapValues { REDACTED }}, activeKeyId=$activeKeyId)"
    }

    data class Tokens(
        val accessToken: Duration = Duration.ofMinutes(5),
        /** A refresh token expires after this long without use. */
        val refreshTokenIdle: Duration = Duration.ofDays(30),
        /** No refresh extends a login beyond this. */
        val maxLoginAge: Duration = Duration.ofDays(90),
    )

    /** Where the first-party apps receive their authorization codes. */
    data class Clients(
        val webRedirectPath: String = "/auth/callback",
        /** An RFC 8252 loopback redirect: the desktop app may listen on any port. */
        val desktopRedirectUri: URI = URI.create("http://127.0.0.1/callback"),
        val mobileRedirectUri: URI = URI.create("io.ogwars.cloud:/oauth2redirect"),
        /** Grafana's confidential client (D-0153); without it, Grafana has no single sign-on. */
        val grafana: Grafana? = null,
    )

    data class Grafana(
        /** `/login/generic_oauth` at Grafana's root URL. */
        val redirectUri: URI,
        /** Synced from Infisical (D-0044). */
        val clientSecret: String,
        /** Grafana's session ends with its access token, since it gets no refresh token (D-0185). */
        val session: Duration = Duration.ofHours(8),
    ) {
        init {
            require(clientSecret.isNotBlank()) { "ogcloud.auth.clients.grafana.client-secret must not be blank" }
            require(session.isPositive) { "ogcloud.auth.clients.grafana.session must be positive" }
        }

        override fun toString() = "Grafana(redirectUri=$redirectUri, clientSecret=$REDACTED, session=$session)"
    }
}
