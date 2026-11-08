package io.ogwars.cloud.bukkit

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.Server
import org.junit.jupiter.api.Test
import java.util.logging.Logger

class EnableOrStopTest {
    private val server = mockk<Server>(relaxed = true)

    @Test
    fun `a backend whose OgCloud fails to start stops instead of taking unchecked logins`() {
        shouldThrow<IllegalStateException> {
            enableOrStop(
                server,
                Logger.getAnonymousLogger(),
            ) { error("no gateway CA") }
        }

        verify { server.shutdown() }
    }

    @Test
    fun `a started backend keeps running`() {
        enableOrStop(server, Logger.getAnonymousLogger()) { }

        verify(exactly = 0) { server.shutdown() }
    }
}
