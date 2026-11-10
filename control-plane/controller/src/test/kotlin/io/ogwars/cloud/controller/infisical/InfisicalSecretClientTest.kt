package io.ogwars.cloud.controller.infisical

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** The requests bootstrap's Go client makes against a real Infisical in its tests (D-0152). */
class InfisicalSecretClientTest {
    @TempDir
    lateinit var dir: Path

    private val builder = RestClient.builder().baseUrl(BASE)
    private val server = MockRestServiceServer.bindTo(builder).build()

    @Test
    fun `reads the tokens as the controller's identity`() {
        expectLogin()
        server
            .expect(requestTo("$BASE/api/v4/secrets/TOKENS?$READ_QUERY"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer access-1"))
            .andRespond(json("""{"secret":{"secretKey":"TOKENS","secretValue":"a\nb"}}"""))

        client().read() shouldBe "a\nb"
        server.verify()
    }

    @Test
    fun `a missing secret reads as none`() {
        expectLogin()
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND))

        client().read().shouldBeNull()
    }

    // Read as empty, the next rotation would write the new token alone and cut the proxies off.
    @Test
    fun `an answer without the value is an error, not an empty secret`() {
        expectLogin()
        server.expect(method(HttpMethod.GET)).andRespond(json("""{"secret":{"secretKey":"TOKENS"}}"""))

        shouldThrow<IllegalStateException> { client().read() }
    }

    @Test
    fun `replaces the value in place`() {
        expectLogin()
        server
            .expect(requestTo("$BASE/api/v4/secrets/TOKENS"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(header("Authorization", "Bearer access-1"))
            .andExpect(jsonPath("$.projectId").value("project-1"))
            .andExpect(jsonPath("$.environment").value("prod"))
            .andExpect(jsonPath("$.secretPath").value("/"))
            .andExpect(jsonPath("$.secretValue").value("a\nb"))
            .andRespond(json("""{"secret":{}}"""))

        client().write("a\nb")
        server.verify()
    }

    /** Kubernetes auth with the identity id and the service-account token from their files, then the project. */
    private fun expectLogin() {
        server
            .expect(requestTo("$BASE/api/v1/auth/kubernetes-auth/login"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.identityId").value("identity-1"))
            .andExpect(jsonPath("$.jwt").value("service-account-token"))
            .andRespond(json("""{"accessToken":"access-1","expiresIn":7200,"tokenType":"Bearer"}"""))
        server
            .expect(requestTo("$BASE/api/v1/projects/slug/ogcloud-forwarding"))
            .andExpect(header("Authorization", "Bearer access-1"))
            .andRespond(json("""{"id":"project-1","slug":"ogcloud-forwarding","environments":[{"slug":"prod"}]}"""))
    }

    private fun client(): InfisicalSecretClient {
        val identity = Files.writeString(dir.resolve("identity"), "identity-1\n")
        val token = Files.writeString(dir.resolve("token"), "service-account-token\n")
        val properties = InfisicalProperties(url = URI.create(BASE), identityIdFile = identity, tokenFile = token)
        return InfisicalSecretClient(builder.build(), properties)
    }

    private fun json(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private companion object {
        const val BASE = "https://ogcloud-infisical.ogcloud.svc:8443"
        const val READ_QUERY = "projectId=project-1&environment=prod&secretPath=%2F&viewSecretValue=true"
    }
}
