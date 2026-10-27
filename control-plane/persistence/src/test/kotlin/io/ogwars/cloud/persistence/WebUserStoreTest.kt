package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.Role
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@PersistenceTest
class WebUserStoreTest(
    @Autowired private val users: WebUserStore,
    @Autowired private val roles: RoleStore,
    @Autowired private val players: PlayerStore,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val transactions: TransactionTemplate,
) {
    private val user = WebUser(UUID(0, 7), "Jevzo", "Jevzo@Example.com", "{bcrypt}hash", "admin", NOW)

    @Test
    fun `users are found by id and by username or email, ignoring case`() {
        users.create(user)

        users.find(user.id)?.value shouldBe user
        users.findByLogin("jevzo") shouldBe user
        users.findByLogin("jevzo@example.com") shouldBe user
        users.findByLogin("someone") shouldBe null
    }

    // One duplicate per test: the failed insert aborts the test's transaction.
    @Test
    fun `usernames are unique regardless of case`() {
        users.create(user)

        shouldThrow<ConflictException> {
            users.create(user.copy(id = UUID(0, 8), username = "JEVZO", email = "other@example.com"))
        }
    }

    @Test
    fun `emails are unique regardless of case`() {
        users.create(user)

        shouldThrow<ConflictException> {
            users.create(user.copy(id = UUID(0, 8), username = "other", email = "jevzo@EXAMPLE.com"))
        }
    }

    @Test
    fun `users are listed by id in pages`() {
        val ids = (1L..3L).map { UUID(1, it) }
        ids.forEach {
            users.create(user.copy(id = it, username = "user-${it.leastSignificantBits}", email = "$it@example.com"))
        }

        users.list(after = null, limit = 2).map { it.value.id } shouldContainExactly ids.take(2)
        users.list(after = ids[1], limit = 2).map { it.value.id } shouldContainExactly ids.drop(2)
    }

    @Test
    fun `a user is found by the Minecraft account linked to it`() {
        val player = UUID(0, 0xbeef)
        players.recordSeen(player, "Jevzo", NOW, PlayerRank(player, "default", null))
        users.create(user.copy(linkedPlayerUuid = player))

        users.findByLinkedPlayer(player)?.value?.id shouldBe user.id
        users.findByLinkedPlayer(UUID(0, 1)) shouldBe null
        shouldThrow<ConflictException> {
            users.create(
                WebUser(UUID(0, 8), "Other", "other@example.com", null, "admin", NOW, linkedPlayerUuid = player),
            )
        }
    }

    @Test
    fun `updates need the version that was read`() {
        users.create(user)

        users.update(0, user.copy(email = "new@example.com"))

        users.find(user.id)?.revision shouldBe 1
        shouldThrow<StaleUpdateException> { users.update(0, user.copy(enabled = false)) }
        shouldThrow<NoSuchElementException> { users.update(0, user.copy(id = UUID(9, 9))) }
    }

    @Test
    fun `the last enabled owner can be neither demoted, disabled nor deleted`() {
        val owner = user.copy(roleId = BuiltInRole.OWNER.id)
        users.create(owner)

        shouldThrow<ConflictException> { users.update(0, owner.copy(roleId = "admin")) }
        shouldThrow<ConflictException> { users.update(0, owner.copy(enabled = false)) }
        shouldThrow<ConflictException> { users.delete(owner.id) }
    }

    @Test
    fun `with a second owner the first may go`() {
        val owner = user.copy(roleId = BuiltInRole.OWNER.id)
        users.create(owner)
        users.create(owner.copy(id = UUID(0, 8), username = "second", email = "second@example.com"))

        users.update(0, owner.copy(roleId = "admin"))

        users.find(owner.id)?.value?.roleId shouldBe "admin"
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `two concurrent demotions cannot remove the last two owners`() {
        val first =
            user.copy(
                id = UUID(2, 1),
                username = "owner-one",
                email = "one@example.com",
                roleId = BuiltInRole.OWNER.id,
            )
        val second = first.copy(id = UUID(2, 2), username = "owner-two", email = "two@example.com")
        users.create(first)
        users.create(second)
        try {
            lateinit var other: CompletableFuture<Unit>
            transactions.executeWithoutResult {
                users.update(0, first.copy(roleId = "admin"))
                // Runs while this transaction holds the owner locks, so it waits for the commit below.
                other = CompletableFuture.supplyAsync { users.update(0, second.copy(roleId = "admin")) }
            }

            shouldThrow<ExecutionException> { other.get() }.cause?.javaClass shouldBe ConflictException::class.java
            users.find(second.id)?.value?.isOwner shouldBe true
        } finally {
            jdbc.sql("delete from web_users where id in (:ids)").param("ids", listOf(first.id, second.id)).update()
        }
    }

    @Test
    fun `only the first owner is created by setup`() {
        val owner = user.copy(roleId = BuiltInRole.OWNER.id)

        users.ownerExists() shouldBe false
        users.createFirstOwner(owner) shouldBe true
        users.createFirstOwner(owner.copy(id = UUID(0, 8), username = "late", email = "late@example.com")) shouldBe
            false
        users.ownerExists() shouldBe true
    }

    @Test
    fun `passwords are replaced`() {
        users.create(user)

        users.setPasswordHash(user.id, "{bcrypt}new")

        users.find(user.id)?.value?.passwordHash shouldBe "{bcrypt}new"
        shouldThrow<NoSuchElementException> { users.setPasswordHash(UUID(9, 9), "{bcrypt}x") }
    }

    @Test
    fun `custom roles keep their permissions, built-in roles take theirs from code`() {
        roles.create(Role("builders", "Builders", setOf(ApiPermission.TEMPLATES_WRITE, ApiPermission.TEMPLATES_READ)))
        jdbc.sql("insert into role_permissions (role_id, permission) values ('builders', 'gone:read')").update()

        roles.find("builders")?.value?.permissions shouldBe
            setOf(ApiPermission.TEMPLATES_WRITE, ApiPermission.TEMPLATES_READ)
        roles.find("owner")?.value?.permissions shouldBe BuiltInRole.OWNER.permissions
        roles.findAll().map { it.value.id } shouldContainExactly
            listOf("admin", "moderator", "owner", "viewer", "builders")
    }

    @Test
    fun `custom roles change against their version`() {
        roles.create(Role("builders", "Builders", setOf(ApiPermission.TEMPLATES_WRITE)))

        roles.update(0, Role("builders", "Map builders", setOf(ApiPermission.NPCS_WRITE)))

        roles.find("builders")?.value shouldBe Role("builders", "Map builders", setOf(ApiPermission.NPCS_WRITE))
        shouldThrow<StaleUpdateException> { roles.update(0, Role("builders", "Again", emptySet())) }
    }

    @Test
    fun `built-in roles cannot change`() {
        shouldThrow<ConflictException> { roles.update(0, Role("owner", "Boss", emptySet(), builtIn = true)) }
        shouldThrow<ConflictException> { roles.delete("viewer") }
    }

    @Test
    fun `a role in use cannot be deleted`() {
        roles.create(Role("builders", "Builders", emptySet()))
        users.create(user.copy(roleId = "builders"))

        shouldThrow<ConflictException> { roles.delete("builders") }
    }

    @Test
    fun `an unused custom role is deleted`() {
        roles.create(Role("builders", "Builders", setOf(ApiPermission.TEMPLATES_WRITE)))

        roles.delete("builders")

        roles.find("builders") shouldBe null
    }
}
