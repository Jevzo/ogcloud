package io.ogwars.cloud.api.events

import io.ogwars.cloud.api.audit.NotAudited
import io.ogwars.cloud.api.web.Anonymous
import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.api.web.callerPermissions
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** Live updates over Server-Sent Events (D-0018, D-0105). */
@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val tickets: EventTickets,
    private val events: LiveEvents,
) {
    /** A single-use ticket for the stream, valid 30 seconds, carrying the caller's permissions. */
    @AnyCaller
    @NotAudited
    @PostMapping("/tickets")
    fun ticket(): EventTicket = tickets.issue(callerPermissions())

    /** Events `server`, `job`, `scaling` and `alert`, each only with the permission it needs; at-most-once. */
    @Anonymous
    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @ApiResponse(
        responseCode = "200",
        description = "The event name tells which schema its data has",
        content = [
            Content(
                mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                schema = Schema(oneOf = [ServerEvent::class, JobEvent::class, ScalingEvent::class, AlertEvent::class]),
            ),
        ],
    )
    fun stream(
        @RequestParam ticket: String,
    ): SseEmitter {
        val permissions = tickets.redeem(ticket) ?: throw AccessDeniedException("The ticket was used or has expired")
        return events.subscribe(permissions)
    }
}
