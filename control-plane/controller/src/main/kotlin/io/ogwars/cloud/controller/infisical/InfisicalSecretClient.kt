package io.ogwars.cloud.controller.infisical

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.nio.file.Files
import java.nio.file.Path

/** One secret in Infisical, read and replaced over its REST API as the controller's identity. */
interface InfisicalSecret {
    /** The value, or null when the secret does not exist. */
    fun read(): String?

    fun write(value: String)
}

/**
 * Logs in with Kubernetes auth for each call: rotations are rare, and the projected token on disk is always the
 * current one. The same calls as bootstrap's client (data-plane/bootstrap/infisical), which is tested against
 * a real Infisical.
 */
class InfisicalSecretClient(
    private val http: RestClient,
    private val properties: InfisicalProperties,
) : InfisicalSecret {
    override fun read(): String? {
        val session = session()
        return try {
            http
                .get()
                .uri(
                    "/api/v4/secrets/{name}?projectId={project}&environment={environment}&secretPath={path}&viewSecretValue=true",
                    properties.secretName,
                    session.projectId,
                    properties.environment,
                    properties.secretPath,
                ).headers { it.setBearerAuth(session.accessToken) }
                .retrieve()
                .body<SecretResponse>()
                ?.secret
                ?.secretValue
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) null else throw e
        }
    }

    override fun write(value: String) {
        val session = session()
        http
            .patch()
            .uri("/api/v4/secrets/{name}", properties.secretName)
            .headers { it.setBearerAuth(session.accessToken) }
            .body(
                mapOf(
                    "projectId" to session.projectId,
                    "environment" to properties.environment,
                    "secretPath" to properties.secretPath,
                    "secretValue" to value,
                ),
            ).retrieve()
            .toBodilessEntity()
    }

    private fun session(): Session {
        val login =
            http
                .post()
                .uri("/api/v1/auth/kubernetes-auth/login")
                .body(
                    mapOf("identityId" to fileText(properties.identityIdFile), "jwt" to fileText(properties.tokenFile)),
                ).retrieve()
                .body<LoginResponse>()
        val accessToken =
            login?.accessToken?.takeIf(String::isNotEmpty)
                ?: error("Infisical's Kubernetes login answered without an access token")
        val project =
            http
                .get()
                .uri("/api/v1/projects/slug/{slug}", properties.project)
                .headers { it.setBearerAuth(accessToken) }
                .retrieve()
                .body<ProjectResponse>()
                ?: error("Infisical answered without project ${properties.project}")
        return Session(accessToken, project.id)
    }

    private fun fileText(file: Path?): String =
        Files
            .readString(
                requireNotNull(file) {
                    "checked in InfisicalProperties"
                },
            ).trim()

    private data class Session(
        val accessToken: String,
        val projectId: String,
    )

    private data class LoginResponse(
        val accessToken: String?,
    )

    private data class ProjectResponse(
        val id: String,
    )

    private data class SecretResponse(
        val secret: SecretValue?,
    )

    private data class SecretValue(
        val secretValue: String?,
    )
}
