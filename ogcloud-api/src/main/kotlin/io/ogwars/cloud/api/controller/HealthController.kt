package io.ogwars.cloud.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health")
class HealthController {
    @GetMapping("/health")
    @Operation(summary = "Check API health")
    fun health(): Map<String, String> = mapOf("status" to "ok")
}
