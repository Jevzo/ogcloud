package io.ogwars.cloud.api.web

import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.StaleUpdateException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Domain failures as RFC 9457 problem details; everything else keeps Spring's own mapping. */
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(e: NoSuchElementException): ProblemDetail = problem(HttpStatus.NOT_FOUND, e)

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, e)

    /** Someone changed the record since the caller read it (D-0069): reload and retry. */
    @ExceptionHandler(StaleUpdateException::class)
    fun stale(e: StaleUpdateException): ProblemDetail = problem(HttpStatus.CONFLICT, e)

    /** The change contradicts current state, e.g. it would remove the last owner. */
    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException): ProblemDetail = problem(HttpStatus.CONFLICT, e)

    private fun problem(
        status: HttpStatus,
        e: Exception,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, e.message ?: status.reasonPhrase)
}
