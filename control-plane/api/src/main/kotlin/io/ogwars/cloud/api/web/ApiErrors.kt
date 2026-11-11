package io.ogwars.cloud.api.web

import io.ogwars.cloud.api.auth.users.WrongSetupTokenException
import io.ogwars.cloud.api.templates.UploadTooLargeException
import io.ogwars.cloud.api.text.TextErrorView
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.ogwars.cloud.domain.text.InvalidTextException
import io.ogwars.cloud.messaging.PublishException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Domain failures of the REST API as RFC 9457 problem details; everything else, the HTML login pages included,
 * keeps Spring's own mapping.
 */
@RestControllerAdvice(annotations = [RestController::class])
class ApiErrors {
    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(e: NoSuchElementException): ProblemDetail = problem(HttpStatus.NOT_FOUND, e)

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, e)

    /** In-game texts a field may not hold, each error coded so the dashboard can show it at its place (D-0205). */
    @ExceptionHandler(InvalidTextException::class)
    fun invalidText(e: InvalidTextException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, e).apply {
            setProperty(TEXT_ERRORS, e.errors.mapValues { (_, errors) -> errors.map(TextErrorView::of) })
        }

    /** Someone changed the record since the caller read it (D-0069): reload and retry. */
    @ExceptionHandler(StaleUpdateException::class)
    fun stale(e: StaleUpdateException): ProblemDetail = problem(HttpStatus.CONFLICT, e)

    /** The change contradicts current state, e.g. it would remove the last owner. */
    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException): ProblemDetail = problem(HttpStatus.CONFLICT, e)

    /** A command could not be handed to Kafka; nothing was saved, so retrying is safe. */
    @ExceptionHandler(PublishException::class)
    fun unavailable(e: PublishException): ProblemDetail = problem(HttpStatus.SERVICE_UNAVAILABLE, e)

    @ExceptionHandler(WrongSetupTokenException::class)
    fun wrongSetupToken(e: WrongSetupTokenException): ProblemDetail = problem(HttpStatus.FORBIDDEN, e)

    @ExceptionHandler(UploadTooLargeException::class)
    fun tooLarge(e: UploadTooLargeException): ProblemDetail = problem(HttpStatus.CONTENT_TOO_LARGE, e)

    private companion object {
        const val TEXT_ERRORS = "textErrors"
    }

    private fun problem(
        status: HttpStatus,
        e: Exception,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, e.message ?: status.reasonPhrase)
}
