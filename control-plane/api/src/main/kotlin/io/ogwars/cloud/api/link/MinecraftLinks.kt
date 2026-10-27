package io.ogwars.cloud.api.link

import io.ogwars.cloud.api.auth.Owners
import io.ogwars.cloud.api.events.Commands
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.sha256Hex
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.OneTimeKind
import io.ogwars.cloud.domain.store.OneTimeStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.WebUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class LinkCodeSent(
    val playerUuid: UUID,
    val expiresAt: Instant,
)

/**
 * Links web users to Minecraft accounts with a code shown in game, and keeps their usernames equal to their
 * Minecraft names (D-0106).
 */
@Service
class MinecraftLinks(
    private val users: WebUserStore,
    private val players: PlayerStore,
    private val sessions: SessionStore,
    private val codes: OneTimeStore,
    private val commands: Commands,
    private val owners: Owners,
    private val random: SecureRandom,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Sends a code to [playerName], who must be online; a new request replaces the user's pending code. */
    fun request(
        actor: Actor,
        userId: UUID,
        playerName: String,
    ): LinkCodeSent {
        val player = sessions.findByName(playerName)?.value ?: throw NoSuchElementException("$playerName is not online")
        val code = random.nextInt(CODE_BOUND).toString().padStart(CODE_DIGITS, '0')
        codes.put(
            OneTimeKind.LINK_CODE,
            userId.toString(),
            "${player.playerUuid}$SEPARATOR${sha256Hex(code)}",
            LIFETIME,
        )
        commands.linkCode(actor, player.playerUuid, code)
        return LinkCodeSent(player.playerUuid, clock.instant().plus(LIFETIME))
    }

    /** Links the account the pending code was sent to; a wrong code is used up as well. */
    fun confirm(
        userId: UUID,
        code: String,
    ) {
        val pending =
            codes.take(OneTimeKind.LINK_CODE, userId.toString())
                ?: throw IllegalArgumentException("No link code is pending; request a new one")
        val (player, hash) = pending.split(SEPARATOR)
        require(MessageDigest.isEqual(hash.toByteArray(), sha256Hex(code).toByteArray())) {
            "The code is wrong; request a new one"
        }
        val playerUuid = UUID.fromString(player)
        val holder = users.findByLinkedPlayer(playerUuid)
        if (holder != null && holder.value.id != userId) {
            throw ConflictException("This Minecraft account is linked to another user")
        }
        val user = load(userId)
        val name = players.find(playerUuid)?.name
        val username = name?.takeIf { available(it, userId) } ?: user.value.username
        users.update(user.revision, user.value.copy(linkedPlayerUuid = playerUuid, username = username))
    }

    /** Non-owners may unlink anyone but owners (D-0094), themselves included. */
    fun unlink(
        actor: Actor,
        userId: UUID,
    ) {
        val user = load(userId)
        owners.requireMayManage(actor, user.value)
        users.update(user.revision, user.value.copy(linkedPlayerUuid = null))
    }

    /** Follows a Minecraft name change; a name held by another user keeps the old username. */
    fun syncUsername(
        playerUuid: UUID,
        name: String,
    ) {
        val user = users.findByLinkedPlayer(playerUuid) ?: return
        if (user.value.username == name) return
        if (!available(name, user.value.id)) {
            log.warn("Keeping the username of user {}: {} is taken or invalid", user.value.id, name)
            return
        }
        users.update(user.revision, user.value.copy(username = name))
    }

    /** The current Minecraft names of the users' linked accounts. */
    fun linkedNames(users: Collection<WebUser>): Map<UUID, String> =
        players.names(users.mapNotNull { it.linkedPlayerUuid })

    private fun available(
        name: String,
        userId: UUID,
    ) = name.length in WebUser.USERNAME_LENGTH && users.findByLogin(name)?.id.let { it == null || it == userId }

    private fun load(userId: UUID): Revisioned<WebUser> =
        users.find(userId) ?: throw NoSuchElementException("User $userId does not exist")

    private companion object {
        const val CODE_DIGITS = 6
        const val CODE_BOUND = 1_000_000
        const val SEPARATOR = " "
        val LIFETIME: Duration = Duration.ofMinutes(5)
    }
}
