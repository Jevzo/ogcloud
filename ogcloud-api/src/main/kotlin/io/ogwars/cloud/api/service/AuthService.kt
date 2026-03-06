package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.config.AuthProperties
import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.exception.*
import io.ogwars.cloud.api.kafka.WebAccountLinkOtpProducer
import io.ogwars.cloud.api.model.LinkOtpDocument
import io.ogwars.cloud.api.model.RefreshTokenDocument
import io.ogwars.cloud.api.model.WebUserDocument
import io.ogwars.cloud.api.redis.PlayerRedisRepository
import io.ogwars.cloud.api.repository.LinkOtpRepository
import io.ogwars.cloud.api.repository.PlayerRepository
import io.ogwars.cloud.api.repository.RefreshTokenRepository
import io.ogwars.cloud.api.repository.WebUserRepository
import io.ogwars.cloud.api.security.AuthenticatedUser
import io.ogwars.cloud.api.security.JwtTokenService
import io.ogwars.cloud.api.util.EmailAddressNormalizer
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.*

@Service
class AuthService(
    private val webUserRepository: WebUserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val linkOtpRepository: LinkOtpRepository,
    private val playerRepository: PlayerRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
    private val webAccountLinkOtpProducer: WebAccountLinkOtpProducer,
    private val authProperties: AuthProperties,
    private val clock: Clock
) {

    private val secureRandom = SecureRandom()

    fun login(rawEmail: String, password: String): AuthTokenResponse {
        val email = EmailAddressNormalizer.normalize(rawEmail)
        val user = webUserRepository.findByEmail(email).orElse(null)

        val passwordMatches = user?.let {
            passwordEncoder.matches(password, it.passwordHash)
        } == true

        if (user == null || !passwordMatches) {
            throw InvalidCredentialsException()
        }

        return createTokenResponse(user)
    }

    fun refresh(refreshToken: String): AuthTokenResponse {
        val tokenValue = normalizeOpaqueToken(refreshToken, "Refresh token")
        val tokenDocument = findValidRefreshToken(tokenValue)
        val user = findRefreshTokenUser(tokenDocument)

        return buildTokenResponse(user, tokenValue, tokenDocument.expiresAt)
    }

    fun revokeRefreshToken(currentUser: AuthenticatedUser, refreshToken: String) {
        val tokenValue = normalizeOpaqueToken(refreshToken, "Refresh token")
        val tokenDocument = refreshTokenRepository.findByTokenHash(hashToken(tokenValue))
            .orElseThrow { InvalidRefreshTokenException() }

        if (tokenDocument.userId != currentUser.id) {
            throw InvalidRefreshTokenException()
        }

        refreshTokenRepository.delete(tokenDocument)
    }

    fun revokeAllRefreshTokens(currentUser: AuthenticatedUser): RevokeAllTokensResponse {
        val count = revokeAllTokensForUser(currentUser.id)
        return RevokeAllTokensResponse(count)
    }

    fun updateOwnProfile(currentUser: AuthenticatedUser, request: SelfUpdateRequest): WebUserResponse {
        val user = requireUser(currentUser.id)

        if (request.email == null && request.password == null) {
            throw IllegalArgumentException("At least one field must be provided")
        }

        val normalizedEmail = request.email?.let(EmailAddressNormalizer::normalize)
        if (normalizedEmail != null && normalizedEmail != user.email && webUserRepository.existsByEmail(normalizedEmail)) {
            throw WebUserAlreadyExistsException(normalizedEmail)
        }

        val saved = webUserRepository.save(
            user.copy(
                email = normalizedEmail ?: user.email,
                passwordHash = request.password?.let(passwordEncoder::encode) ?: user.passwordHash
            )
        )

        if (request.password != null) {
            revokeAllTokensForUser(saved.id)
        }

        return saved.toResponse()
    }

    fun requestAccountLinkOtp(currentUser: AuthenticatedUser, minecraftUsername: String) {
        val player = playerRepository.findByNameIgnoreCase(minecraftUsername.trim())
            ?: throw PlayerLinkUnavailableException("Minecraft account not found: $minecraftUsername")

        if (!playerRedisRepository.isOnline(player.id)) {
            throw PlayerLinkUnavailableException("Minecraft account is not online: ${player.name}")
        }

        linkOtpRepository.findByUserId(currentUser.id).ifPresent(linkOtpRepository::delete)

        val now = now()
        val otp = generateOtp()

        linkOtpRepository.save(
            LinkOtpDocument(
                id = UUID.randomUUID().toString(),
                userId = currentUser.id,
                playerUuid = player.id,
                otpHash = hashToken(otp),
                expiresAt = now.plus(authProperties.linkOtpLifetime),
                createdAt = now
            )
        )

        webAccountLinkOtpProducer.publishOtp(player.id, otp, currentUser.email)
    }

    fun confirmAccountLinkOtp(currentUser: AuthenticatedUser, otp: String): WebUserResponse {
        val user = requireUser(currentUser.id)
        val normalizedOtp = normalizeOpaqueToken(otp, "OTP")
        val otpDocument = linkOtpRepository.findByUserId(currentUser.id).orElseThrow { InvalidLinkOtpException() }

        if (otpDocument.expiresAt.isBefore(now())) {
            linkOtpRepository.delete(otpDocument)
            throw InvalidLinkOtpException()
        }

        if (otpDocument.otpHash != hashToken(normalizedOtp)) {
            throw InvalidLinkOtpException()
        }

        val player = playerRepository.findById(otpDocument.playerUuid).orElseThrow { InvalidLinkOtpException() }
        val existingLink = webUserRepository.findByLinkedPlayerUuid(player.id).orElse(null)

        if (existingLink != null && existingLink.id != user.id) {
            throw IllegalArgumentException("Minecraft account is already linked to another web user")
        }

        val saved = webUserRepository.save(
            user.copy(
                username = player.name,
                linkedPlayerUuid = player.id
            )
        )

        linkOtpRepository.delete(otpDocument)

        return saved.toResponse()
    }

    private fun createTokenResponse(user: WebUserDocument): AuthTokenResponse {
        val refreshToken = generateRefreshToken()
        val createdAt = now()
        val refreshTokenExpiresAt = createdAt.plus(authProperties.refreshTokenLifetime)

        refreshTokenRepository.save(
            RefreshTokenDocument(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                tokenHash = hashToken(refreshToken),
                expiresAt = refreshTokenExpiresAt,
                createdAt = createdAt
            )
        )

        return buildTokenResponse(user, refreshToken, refreshTokenExpiresAt)
    }

    fun revokeAllTokensForUser(userId: String): Int {
        val tokens = refreshTokenRepository.findAllByUserId(userId)

        if (tokens.isNotEmpty()) {
            refreshTokenRepository.deleteAll(tokens)
        }

        return tokens.size
    }

    private fun findValidRefreshToken(tokenValue: String): RefreshTokenDocument {
        val tokenDocument = refreshTokenRepository.findByTokenHash(hashToken(tokenValue))
            .orElseThrow { InvalidRefreshTokenException() }

        if (tokenDocument.expiresAt.isBefore(now())) {
            refreshTokenRepository.delete(tokenDocument)
            throw InvalidRefreshTokenException()
        }

        return tokenDocument
    }

    private fun findRefreshTokenUser(tokenDocument: RefreshTokenDocument): WebUserDocument {
        return webUserRepository.findById(tokenDocument.userId).orElseThrow {
            refreshTokenRepository.delete(tokenDocument)
            InvalidRefreshTokenException()
        }
    }

    private fun buildTokenResponse(
        user: WebUserDocument,
        refreshToken: String,
        refreshTokenExpiresAt: Instant
    ): AuthTokenResponse {
        val accessToken = jwtTokenService.issueAccessToken(user)

        return AuthTokenResponse(
            accessToken = accessToken.token,
            accessTokenExpiresAt = accessToken.expiresAt,
            refreshToken = refreshToken,
            refreshTokenExpiresAt = refreshTokenExpiresAt,
            user = user.toResponse()
        )
    }

    private fun normalizeOpaqueToken(value: String, fieldName: String): String {
        val normalized = value.trim()

        if (normalized.isEmpty()) {
            throw IllegalArgumentException("$fieldName must not be blank")
        }

        return normalized
    }

    private fun requireUser(userId: String): WebUserDocument {
        return webUserRepository.findById(userId).orElseThrow { InvalidCredentialsException() }
    }

    private fun now(): Instant {
        return Instant.now(clock)
    }

    private fun generateRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateOtp(): String {
        return secureRandom.nextInt(OTP_UPPER_BOUND).toString().padStart(OTP_LENGTH, '0')
    }

    private fun hashToken(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(value.toByteArray(StandardCharsets.UTF_8)))
    }

    companion object {
        private const val REFRESH_TOKEN_BYTES = 32
        private const val OTP_LENGTH = 6
        private const val OTP_UPPER_BOUND = 1_000_000
    }
}
