package io.ogwars.cloud.api.privacy

import io.ogwars.cloud.api.audit.AuditTrail
import io.ogwars.cloud.api.auth.ActiveLogin
import io.ogwars.cloud.api.auth.Logins
import io.ogwars.cloud.api.auth.Owners
import io.ogwars.cloud.api.auth.mfa.Passkeys
import io.ogwars.cloud.api.auth.mfa.TotpService
import io.ogwars.cloud.api.auth.users.UserView
import io.ogwars.cloud.api.auth.users.toView
import io.ogwars.cloud.api.link.MinecraftLinks
import io.ogwars.cloud.api.live.PlayerView
import io.ogwars.cloud.api.live.toView
import io.ogwars.cloud.api.logs.AuditEntryView
import io.ogwars.cloud.api.logs.toView
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.privacy.Pseudonymizer
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.PseudonymStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.store.WebUserStore
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

data class PlayerExport(
    val player: PlayerView,
    val linkedUserId: UUID?,
    val auditEntries: List<AuditEntryView>,
)

data class PasskeyView(
    val label: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
)

data class UserExport(
    val user: UserView,
    val totpEnabled: Boolean,
    val passkeys: List<PasskeyView>,
    val logins: List<ActiveLogin>,
    val auditEntries: List<AuditEntryView>,
)

/**
 * Exports everything stored about a player or web user, and erases it (D-0049, D-0107). Records that must stay,
 * such as audit entries, keep a pseudonym instead of the person's id; every export and erasure is audited.
 */
@Service
class Privacy(
    private val players: PlayerStore,
    private val sessions: SessionStore,
    private val users: WebUserStore,
    private val audit: AuditLogStore,
    private val pseudonyms: PseudonymStore,
    private val readModels: ReadModelStore,
    private val links: MinecraftLinks,
    private val totp: TotpService,
    private val passkeys: Passkeys,
    private val logins: Logins,
    private val owners: Owners,
    private val pseudonymizer: Pseudonymizer,
    private val trail: AuditTrail,
    private val transactions: TransactionTemplate,
) {
    fun exportPlayer(
        actor: Actor,
        uuid: UUID,
    ): PlayerExport {
        val player = players.find(uuid) ?: throw NoSuchElementException("Nothing is stored about player $uuid")
        val export =
            PlayerExport(
                player.toView(sessions.get(uuid)?.value),
                users.findByLinkedPlayer(uuid)?.value?.id,
                audit.about(uuid.toString()).map { it.toView() },
            )
        trail.record(actor, "GET $PRIVACY$PLAYER", TARGET_TYPE, uuid.toString())
        return export
    }

    /** Refused while the player is online, since the next heartbeat would store them again. */
    fun erasePlayer(
        actor: Actor,
        uuid: UUID,
    ) {
        if (sessions.get(uuid) != null) throw ConflictException("Player $uuid is online; erase them once they leave")
        val pseudonym = pseudonymizer.pseudonym(uuid.toString())
        transactions.executeWithoutResult {
            val deleted = players.delete(uuid)
            val pseudonymised = pseudonyms.pseudonymise(uuid.toString(), pseudonym)
            if (!deleted && pseudonymised == 0) throw NoSuchElementException("Nothing is stored about player $uuid")
        }
        readModels.delete(ReadModelKind.PLAYER_PROFILE, uuid.toString())
        recordErasure(actor, "POST $PRIVACY$PLAYER$ERASURE", uuid.toString(), pseudonym)
    }

    fun exportUser(
        actor: Actor,
        id: UUID,
    ): UserExport {
        val user = users.find(id) ?: throw NoSuchElementException("User $id does not exist")
        val export =
            UserExport(
                user.toView(links.linkedNames(listOf(user.value)).values.firstOrNull()),
                totp.isEnrolled(id),
                passkeys.list(id).map { PasskeyView(it.label, it.created, it.lastUsed) },
                logins.active(id),
                audit.about(id.toString()).map { it.toView() },
            )
        trail.record(actor, "GET $PRIVACY$USER", TARGET_TYPE, id.toString())
        return export
    }

    /** Deletes the account with its passkeys, TOTP and logins; non-owners never erase owners (D-0094). */
    fun eraseUser(
        actor: Actor,
        id: UUID,
    ) {
        val user = users.find(id) ?: throw NoSuchElementException("User $id does not exist")
        owners.requireMayManage(actor, user.value)
        val pseudonym = pseudonymizer.pseudonym(id.toString())
        transactions.executeWithoutResult {
            users.delete(id)
            passkeys.deleteAll(id)
            logins.revokeUser(id)
            pseudonyms.pseudonymise(id.toString(), pseudonym)
        }
        recordErasure(actor, "POST $PRIVACY$USER$ERASURE", id.toString(), pseudonym)
    }

    // The entry itself must not name the erased person, even when they erased themselves.
    private fun recordErasure(
        actor: Actor,
        action: String,
        identifier: String,
        pseudonym: String,
    ) = trail.record(if (actor.id == identifier) actor.copy(id = pseudonym) else actor, action, TARGET_TYPE, pseudonym)

    companion object {
        const val PRIVACY = "/api/v1/privacy"
        const val PLAYER = "/players/{uuid}"
        const val USER = "/users/{id}"
        const val ERASURE = "/erasure"
        private const val TARGET_TYPE = "privacy"
    }
}
