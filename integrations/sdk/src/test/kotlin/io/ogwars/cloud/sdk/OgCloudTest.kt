package io.ogwars.cloud.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@OptIn(OgCloudInternalApi::class)
class OgCloudTest {
    @AfterEach
    fun uninstall() = OgCloud.install(null)

    @Test
    fun `plugins get the node OgCloud installed, as the kind it is`() {
        val server = mockk<OgCloudServer>()

        OgCloud.install(server)

        OgCloud.server() shouldBe server
        shouldThrow<IllegalStateException> { OgCloud.proxy() }
    }

    @Test
    fun `asking before OgCloud enabled fails clearly`() {
        shouldThrow<IllegalStateException> { OgCloud.server() }.message shouldBe
            "OgCloud is not running on a server here"
    }
}
