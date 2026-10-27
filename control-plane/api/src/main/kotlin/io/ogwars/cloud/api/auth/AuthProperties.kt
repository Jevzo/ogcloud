package io.ogwars.cloud.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.nio.file.Path
import java.time.Duration

/** `ogcloud.auth.*`: the built-in OIDC provider (D-0012, D-0090). */
@ConfigurationProperties("ogcloud.auth")
data class AuthProperties(
    /** The public origin, e.g. `https://cloud.example.com`; it is also the OIDC issuer (D-0051). */
    val issuer: URI,
    val signing: Signing,
    val tokens: Tokens = Tokens(),
    val clients: Clients = Clients(),
    /** Creates the first owner; bootstrap puts it into Infisical and it is useless once an owner exists (D-0094). */
    val setupToken: String? = null,
) {
    init {
        require(
            issuer.isAbsolute && issuer.path.isNullOrEmpty(),
        ) { "ogcloud.auth.issuer must be an origin without a path" }
    }

    data class Signing(
        /** Holds `<kid>.pem` RSA private keys (PKCS#8), synced from Infisical (D-0044). */
        val keysDir: Path,
        val activeKeyId: String,
    )

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
    )
}
