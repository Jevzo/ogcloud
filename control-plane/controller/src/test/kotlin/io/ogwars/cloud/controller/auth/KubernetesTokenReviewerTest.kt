package io.ogwars.cloud.controller.auth

import io.ogwars.cloud.controller.config.GrpcAuthProperties
import io.ogwars.cloud.domain.server.T0
import io.fabric8.kubernetes.client.KubernetesClient
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset

class KubernetesTokenReviewerTest {
    private val client = mockk<KubernetesClient>()
    private val reviewer = KubernetesTokenReviewer(client, GrpcAuthProperties(), Clock.fixed(T0, ZoneOffset.UTC))

    @Test
    fun `anything that is not a JWT is refused without asking the API server`() {
        reviewer.username("not-a-token") shouldBe null
        reviewer.username("a.b") shouldBe null
        reviewer.username("x".repeat(9000) + ".a.b") shouldBe null

        verify(exactly = 0) { client.authentication() }
    }
}
