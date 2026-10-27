package io.ogwars.cloud.api

import io.ogwars.cloud.api.web.OpenApiConfiguration
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OperationIdsTest {
    @Test
    fun `resources are the singular, camel-case last path segment`() {
        OpenApiConfiguration.resourceName("/api/v1/users") shouldBe "user"
        OpenApiConfiguration.resourceName("/api/v1/oauth-clients") shouldBe "oauthClient"
        OpenApiConfiguration.resourceName("/api/v1/me") shouldBe "me"
        OpenApiConfiguration.resourceName("/api/v1/access") shouldBe "access"
    }
}
