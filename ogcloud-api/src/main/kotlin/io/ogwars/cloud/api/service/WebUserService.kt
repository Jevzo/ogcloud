package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.CreateWebUserRequest
import io.ogwars.cloud.api.dto.PaginatedResponse
import io.ogwars.cloud.api.dto.PaginationSupport
import io.ogwars.cloud.api.dto.UpdateWebUserRequest
import io.ogwars.cloud.api.dto.WebUserResponse
import io.ogwars.cloud.api.dto.parseRole
import io.ogwars.cloud.api.dto.toResponse
import io.ogwars.cloud.api.exception.WebUserAlreadyExistsException
import io.ogwars.cloud.api.exception.WebUserNotFoundException
import io.ogwars.cloud.api.model.WebUserDocument
import io.ogwars.cloud.api.model.WebUserRole
import io.ogwars.cloud.api.repository.LinkOtpRepository
import io.ogwars.cloud.api.repository.WebUserRepository
import io.ogwars.cloud.api.util.EmailAddressNormalizer
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.count
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WebUserService(
    private val webUserRepository: WebUserRepository,
    private val linkOtpRepository: LinkOtpRepository,
    private val mongoTemplate: MongoTemplate,
    private val passwordEncoder: PasswordEncoder,
    private val authService: AuthService,
) {
    fun listUsers(
        query: String?,
        page: Int,
        size: Int?,
    ): PaginatedResponse<WebUserResponse> {
        val pageRequest = PaginationSupport.toPageRequest(page, size)
        val queryObject = Query()

        PaginationSupport
            .buildSearchCriteria(
                query,
                "email",
                "username",
                "role",
                "linkedPlayerUuid",
            )?.let(queryObject::addCriteria)

        val totalItems = mongoTemplate.count<WebUserDocument>(queryObject)

        queryObject.with(Sort.by(Sort.Order.asc("email"))).with(pageRequest)

        val users =
            mongoTemplate
                .find<WebUserDocument>(queryObject)
                .map { it.toResponse() }

        return PaginationSupport.toResponse(users, page, pageRequest.pageSize, totalItems)
    }

    fun createUser(request: CreateWebUserRequest): WebUserResponse {
        val email = EmailAddressNormalizer.normalize(request.email)

        if (webUserRepository.existsByEmail(email)) {
            throw WebUserAlreadyExistsException(email)
        }

        val saved =
            webUserRepository.save(
                WebUserDocument(
                    id = UUID.randomUUID().toString(),
                    email = email,
                    username = DEFAULT_USERNAME,
                    passwordHash = passwordEncoder.encode(request.password),
                    role = WebUserRole.parse(request.role),
                ),
            )

        return saved.toResponse()
    }

    fun updateUser(
        targetEmail: String,
        request: UpdateWebUserRequest,
    ): WebUserResponse {
        validateUpdateRequest(request)

        val normalizedTargetEmail = EmailAddressNormalizer.normalize(targetEmail)
        val user =
            webUserRepository
                .findByEmail(normalizedTargetEmail)
                .orElseThrow { WebUserNotFoundException(normalizedTargetEmail) }

        val updatedEmail = request.email?.let(EmailAddressNormalizer::normalize)
        if (updatedEmail != null && updatedEmail != user.email && webUserRepository.existsByEmail(updatedEmail)) {
            throw WebUserAlreadyExistsException(updatedEmail)
        }

        val saved =
            webUserRepository.save(
                user.copy(
                    email = updatedEmail ?: user.email,
                    passwordHash = request.password?.let(passwordEncoder::encode) ?: user.passwordHash,
                    username = request.username?.trim() ?: user.username,
                    role = request.parseRole() ?: user.role,
                ),
            )

        if (request.password != null) {
            authService.revokeAllTokensForUser(saved.id)
        }

        return saved.toResponse()
    }

    fun deleteUser(targetEmail: String) {
        val normalizedTargetEmail = EmailAddressNormalizer.normalize(targetEmail)
        val user =
            webUserRepository
                .findByEmail(normalizedTargetEmail)
                .orElseThrow { WebUserNotFoundException(normalizedTargetEmail) }

        webUserRepository.delete(user)

        authService.revokeAllTokensForUser(user.id)
    }

    fun unlinkUserAccount(targetEmail: String): WebUserResponse {
        val normalizedTargetEmail = EmailAddressNormalizer.normalize(targetEmail)
        val user =
            webUserRepository
                .findByEmail(normalizedTargetEmail)
                .orElseThrow { WebUserNotFoundException(normalizedTargetEmail) }

        linkOtpRepository
            .findByUserId(user.id)
            .ifPresent(linkOtpRepository::delete)

        val saved =
            webUserRepository.save(
                user.copy(
                    username = DEFAULT_USERNAME,
                    linkedPlayerUuid = null,
                ),
            )

        return saved.toResponse()
    }

    private fun validateUpdateRequest(request: UpdateWebUserRequest) {
        if (request.email == null && request.password == null && request.username == null && request.role == null) {
            throw IllegalArgumentException("At least one field must be provided")
        }
    }

    companion object {
        private const val DEFAULT_USERNAME = "To be linked"
    }
}
