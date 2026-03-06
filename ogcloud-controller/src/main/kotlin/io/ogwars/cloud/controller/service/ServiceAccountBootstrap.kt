package io.ogwars.cloud.controller.service

import io.ogwars.cloud.controller.config.ServiceAccountProperties
import io.ogwars.cloud.controller.model.WebUserDocument
import io.ogwars.cloud.controller.repository.WebUserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ServiceAccountBootstrap(
    private val webUserRepository: WebUserRepository,
    private val serviceAccountProperties: ServiceAccountProperties,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val normalizedEmail = normalizeEmail(serviceAccountProperties.email)
        val password = serviceAccountProperties.password

        require(password.isNotBlank()) { "OGCLOUD_API_PASSWORD must not be blank" }

        val existing = webUserRepository.findByEmail(normalizedEmail).orElse(null)
        if (existing == null) {
            webUserRepository.save(
                WebUserDocument(
                    id = UUID.randomUUID().toString(),
                    email = normalizedEmail,
                    username = DEFAULT_USERNAME,
                    password = passwordEncoder.encode(password),
                    role = SERVICE_ACCOUNT_ROLE
                )
            )

            log.info("Created API service account: email={}", normalizedEmail)
            return
        }

        val passwordMatches = passwordEncoder.matches(password, existing.password)
        if (existing.role == SERVICE_ACCOUNT_ROLE && passwordMatches) {
            log.info("API service account already present: email={}", normalizedEmail)
            return
        }

        webUserRepository.save(
            existing.copy(
                password = if (passwordMatches) existing.password else passwordEncoder.encode(password),
                role = SERVICE_ACCOUNT_ROLE
            )
        )

        log.info("Updated API service account: email={}", normalizedEmail)
    }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        require(normalized.isNotBlank()) { "OGCLOUD_API_EMAIL must not be blank" }
        return normalized
    }

    companion object {
        private const val SERVICE_ACCOUNT_ROLE = "SERVICE"
        private const val DEFAULT_USERNAME = "SYSTEM_USER"
    }
}
