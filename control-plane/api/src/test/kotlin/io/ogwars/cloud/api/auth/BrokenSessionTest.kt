package io.ogwars.cloud.api.auth

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_ISSUER
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.livestate.ValkeyKeys
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.data.redis.RedisIndexedSessionRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * A request that loaded a login session before its id changed can write the old id's attributes back after the
 * rename, leaving a hash without its creation time. Such a session counts as gone and is removed; before, every
 * request carrying its cookie and every token exchange of its user failed.
 */
@ApiTest
class BrokenSessionTest(
    @LocalServerPort private val port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired sessions: RedisIndexedSessionRepository,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val valkey = sessions.sessionRedisOperations

    @Test
    fun `a user whose old session lost its creation time still signs in`() {
        val user = testUsers.create()
        val broken = breakSession(user.id.toString())
        val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"

        val callback = oauth.authorize(FirstPartyClients.WEB, CALLBACK, user.username, TestUsers.PASSWORD, verifier)
        oauth
            .exchange(
                FirstPartyClients.WEB,
                oauth.codeFrom(callback),
                CALLBACK,
                verifier,
            ).accessToken
            .shouldNotBeNull()
        valkey.hasKey(sessionKey(broken)) shouldBe false
    }

    @Test
    fun `a request carrying a broken session's cookie is answered`() {
        val broken = breakSession("someone")
        val cookie = Base64.getEncoder().encodeToString(broken.toByteArray())

        val response =
            HttpClient.newHttpClient().send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/login/state"))
                    .header("Cookie", "SESSION=$cookie")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        response.statusCode() shouldBe OK
    }

    /** Leaves a session hash as the race does, listed under [principal]; returns its id. */
    private fun breakSession(principal: String): String {
        val id = UUID.randomUUID().toString()
        valkey.opsForHash<String, Any>().put(sessionKey(id), "lastAccessedTime", Instant.now().toEpochMilli())
        valkey.opsForSet().add("${ValkeyKeys.WEB_SESSION_NAMESPACE}:index:$PRINCIPAL_INDEX:$principal", id)
        return id
    }

    private fun sessionKey(id: String) = "${ValkeyKeys.WEB_SESSION_NAMESPACE}:sessions:$id"

    private companion object {
        const val CALLBACK = "$TEST_ISSUER/auth/callback"
        const val OK = 200
        val PRINCIPAL_INDEX = FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
    }
}
