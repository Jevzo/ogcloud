package io.ogwars.cloud.api.exception

import io.ogwars.cloud.api.dto.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(
        GroupNotFoundException::class,
        ServerNotFoundException::class,
        PermissionGroupNotFoundException::class,
        PlayerNotFoundException::class,
        TemplateNotFoundException::class,
        WebUserNotFoundException::class
    )
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(
        ex: RuntimeException
    ): ErrorResponse = error(HttpStatus.NOT_FOUND, ex.message ?: "Not found")

    @ExceptionHandler(
        GroupDeletionTimeoutException::class,
        GroupRestartTimeoutException::class,
        GroupAlreadyExistsException::class,
        PermissionGroupAlreadyExistsException::class,
        WebUserAlreadyExistsException::class,
        DuplicateKeyException::class
    )
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleConflict(
        ex: RuntimeException
    ): ErrorResponse = error(HttpStatus.CONFLICT, ex.message ?: "Conflict")

    @ExceptionHandler(
        PlayerNotOnlineException::class,
        IllegalArgumentException::class,
        PlayerLinkUnavailableException::class,
        InvalidLinkOtpException::class
    )
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(
        ex: RuntimeException
    ): ErrorResponse = error(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(InvalidCredentialsException::class, InvalidRefreshTokenException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUnauthorized(
        ex: RuntimeException
    ): ErrorResponse = error(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(
        ex: MethodArgumentNotValidException
    ): ErrorResponse {
        val details = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return error(HttpStatus.BAD_REQUEST, "Validation failed", details)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraintViolation(
        ex: ConstraintViolationException
    ): ErrorResponse {
        val details = ex.constraintViolations.map { violation -> "${violation.propertyPath}: ${violation.message}" }
        return error(HttpStatus.BAD_REQUEST, "Validation failed", details)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleHandlerMethodValidation(
        ex: HandlerMethodValidationException
    ): ErrorResponse {
        val details = ex.allErrors.mapNotNull { error -> error.defaultMessage }
        return error(HttpStatus.BAD_REQUEST, "Validation failed", details)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGeneric(
        ex: Exception
    ): ErrorResponse {
        log.error("Unexpected error occurred", ex)
        return ErrorResponse(status = 500, message = "Internal server error")
    }

    private fun error(status: HttpStatus, message: String, details: List<String> = emptyList()): ErrorResponse {
        log.warn("{}", message)
        return ErrorResponse(status = status.value(), message = message, details = details)
    }
}
