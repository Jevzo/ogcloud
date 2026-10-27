package io.ogwars.cloud.domain.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RoleAndPasswordTest {
    @Test
    fun `custom roles need a slug id that no built-in role uses`() {
        Role("builders", "Builders", setOf(ApiPermission.TEMPLATES_WRITE)).id shouldBe "builders"
        shouldThrow<IllegalArgumentException> { Role("Builders", "Builders", emptySet()) }
        shouldThrow<IllegalArgumentException> { Role("9lives", "Cats", emptySet()) }
        shouldThrow<IllegalArgumentException> { Role("owner", "Fake owner", emptySet()) }
        shouldThrow<IllegalArgumentException> { Role("builders", " ", emptySet()) }
    }

    @Test
    fun `built-in roles are marked as such`() {
        Role("viewer", "Viewer", BuiltInRole.VIEWER.permissions, builtIn = true).builtIn shouldBe true
        shouldThrow<IllegalArgumentException> { Role("builders", "Builders", emptySet(), builtIn = true) }
    }

    @Test
    fun `passwords need 12 characters and fit bcrypt's 72 bytes`() {
        PasswordPolicy.validate("twelve chars")
        shouldThrow<IllegalArgumentException> { PasswordPolicy.validate("eleven char") }
        shouldThrow<IllegalArgumentException> { PasswordPolicy.validate("x".repeat(PasswordPolicy.MAX_BYTES + 1)) }
        // Twelve emoji are twelve characters, though 48 bytes.
        PasswordPolicy.validate("🔒".repeat(PasswordPolicy.MIN_LENGTH))
        shouldThrow<IllegalArgumentException> { PasswordPolicy.validate("🔒".repeat(PasswordPolicy.MAX_BYTES / 4 + 1)) }
    }

    @Test
    fun `owners are recognised by their role`() {
        WebUser(java.util.UUID(0, 1), "jevzo", "j@example.com", null, "owner", java.time.Instant.EPOCH).isOwner shouldBe
            true
        WebUser(java.util.UUID(0, 1), "jevzo", "j@example.com", null, "admin", java.time.Instant.EPOCH).isOwner shouldBe
            false
    }
}
