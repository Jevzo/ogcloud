package io.ogwars.cloud.bukkit

import io.ogwars.cloud.node.core.permission.Decision
import org.bukkit.entity.Player
import org.bukkit.permissions.Permissible
import org.bukkit.permissions.PermissibleBase
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.Plugin
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * As v1, a player holds OgCloud's nodes on top of whatever decided their permissions before, such as op defaults or
 * another permission plugin; everything else, attachments included, stays with that [wrapped] permissible.
 */
class OgCloudPermissible(
    private val player: Player,
    wrapped: PermissibleBase,
    private val decide: (Player, String) -> Decision,
) : PermissibleBase(player) {
    // Null only while PermissibleBase's constructor runs, which calls recalculatePermissions before this is set.
    private val wrappedOrNull: PermissibleBase? = wrapped
    private val previous: PermissibleBase get() = checkNotNull(wrappedOrNull) { "used before construction ended" }

    override fun hasPermission(name: String): Boolean =
        decide(player, name) == Decision.ALLOW || previous.hasPermission(name)

    override fun hasPermission(permission: Permission): Boolean =
        decide(player, permission.name) == Decision.ALLOW || previous.hasPermission(permission)

    override fun isPermissionSet(name: String): Boolean =
        decide(player, name) != Decision.DEFAULT || previous.isPermissionSet(name)

    override fun isPermissionSet(permission: Permission): Boolean =
        decide(player, permission.name) != Decision.DEFAULT || previous.isPermissionSet(permission)

    override fun isOp(): Boolean = previous.isOp

    override fun setOp(value: Boolean) {
        previous.isOp = value
    }

    override fun addAttachment(
        plugin: Plugin,
        name: String,
        value: Boolean,
    ): PermissionAttachment = previous.addAttachment(plugin, name, value)

    override fun addAttachment(plugin: Plugin): PermissionAttachment = previous.addAttachment(plugin)

    override fun addAttachment(
        plugin: Plugin,
        name: String,
        value: Boolean,
        ticks: Int,
    ): PermissionAttachment? = previous.addAttachment(plugin, name, value, ticks)

    override fun addAttachment(
        plugin: Plugin,
        ticks: Int,
    ): PermissionAttachment? = previous.addAttachment(plugin, ticks)

    override fun removeAttachment(attachment: PermissionAttachment) = previous.removeAttachment(attachment)

    override fun recalculatePermissions() {
        wrappedOrNull?.recalculatePermissions()
    }

    override fun clearPermissions() = previous.clearPermissions()

    override fun getEffectivePermissions(): Set<PermissionAttachmentInfo> = previous.effectivePermissions
}

/**
 * Puts an [OgCloudPermissible] into a player. Bukkit has no API for that, so the player's [PermissibleBase] field is
 * replaced, found by type so it works across server versions (v1's approach).
 */
object PermissionInjector {
    private val fields = ConcurrentHashMap<Class<*>, Field>()

    fun inject(
        player: Player,
        decide: (Player, String) -> Decision,
    ) {
        val field = fields.computeIfAbsent(player.javaClass, ::permissibleField)
        val current = field.get(player) as PermissibleBase
        if (current !is OgCloudPermissible) field.set(player, OgCloudPermissible(player, current, decide))
        player.recalculatePermissions()
    }

    private fun permissibleField(type: Class<*>): Field =
        generateSequence(type) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull {
                Permissible::class.java.isAssignableFrom(it.type) &&
                    PermissibleBase::class.java.isAssignableFrom(it.type)
            }?.apply { isAccessible = true }
            ?: throw IllegalStateException("${type.name} has no PermissibleBase field to replace")
}
