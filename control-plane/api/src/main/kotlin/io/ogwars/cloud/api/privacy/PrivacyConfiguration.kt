package io.ogwars.cloud.api.privacy

import io.ogwars.cloud.domain.privacy.Pseudonymizer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Base64

/** `ogcloud.privacy.*`, mounted from Infisical (D-0107). */
@ConfigurationProperties("ogcloud.privacy")
data class PrivacyProperties(
    /** Base64 HMAC key of at least 32 bytes; it never rotates, so an erased person keeps one pseudonym. */
    val pseudonymKey: String,
)

@Configuration(proxyBeanMethods = false)
class PrivacyConfiguration {
    @Bean
    fun pseudonymizer(properties: PrivacyProperties): Pseudonymizer =
        Pseudonymizer(Base64.getDecoder().decode(properties.pseudonymKey))
}
