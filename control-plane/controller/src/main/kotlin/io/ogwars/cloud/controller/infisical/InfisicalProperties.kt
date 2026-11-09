package io.ogwars.cloud.controller.infisical

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.nio.file.Path
import java.time.Duration

/**
 * `ogcloud.controller.infisical.*`: the project holding the forwarding tokens, which the controller rotates
 * (D-0078) and which it alone may change (D-0152). Without a url, rotations fail.
 */
@ConfigurationProperties("ogcloud.controller.infisical")
data class InfisicalProperties(
    val url: URI? = null,
    /** The controller's identity id, as bootstrap publishes it. */
    val identityIdFile: Path? = null,
    /** A projected service-account token, which Infisical reviews with TokenReview. */
    val tokenFile: Path? = null,
    val project: String = "ogcloud-forwarding",
    val environment: String = "prod",
    val secretPath: String = "/",
    val secretName: String = "TOKENS",
    /** The SSL bundle that trusts Infisical's certificate from the internal CA (D-0149). */
    val sslBundle: String? = null,
    val timeout: Duration = Duration.ofSeconds(10),
) {
    init {
        require(url == null || (identityIdFile != null && tokenFile != null)) {
            "ogcloud.controller.infisical needs identity-id-file and token-file with a url"
        }
    }
}
