package io.ogwars.cloud.apitest

import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.ogwars.cloud.messaging.PublishException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Test-only endpoints, outside the scanned API package so they never reach the OpenAPI contract. */
@RestController
class ProbeController {
    @AnyCaller
    @GetMapping("/api/v1/probes/client")
    fun client(request: HttpServletRequest): String = request.remoteAddr

    @AnyCaller
    @GetMapping("/api/v1/probes/missing")
    fun missing(): String = throw NoSuchElementException("Group bedwars does not exist")

    @AnyCaller
    @GetMapping("/api/v1/probes/invalid")
    fun invalid(): String = throw IllegalArgumentException("limit must be between 1 and 200")

    @AnyCaller
    @GetMapping("/api/v1/probes/stale")
    fun stale(): String = throw StaleUpdateException("Group bedwars changed since version 3")

    @AnyCaller
    @GetMapping("/api/v1/probes/unpublished")
    fun unpublished(): String =
        throw PublishException("Publishing to ogcloud.server.command failed", IllegalStateException("broker down"))

    @GetMapping("/api/v1/probes/undeclared")
    fun undeclared(): String = "should never be served"

    @AnyCaller
    @PostMapping("/api/v1/probes/{probeId}/actions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun act(
        @PathVariable probeId: String,
    ) = Unit
}
