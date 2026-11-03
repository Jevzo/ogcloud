package io.ogwars.cloud.sdk

/**
 * Entry point for plugins: `OgCloud.server()` on Paper and PandaSpigot, `OgCloud.proxy()` on Velocity. OgCloud's
 * own plugin installs the implementation when it enables, so call these from your plugin's enable or later.
 */
object OgCloud {
    @Volatile
    private var node: OgCloudNode? = null

    /** The server this plugin runs on; fails on a proxy or before OgCloud enabled. */
    @JvmStatic
    fun server(): OgCloudServer =
        node as? OgCloudServer ?: throw IllegalStateException("OgCloud is not running on a server here")

    /** The proxy this plugin runs on; fails on a server or before OgCloud enabled. */
    @JvmStatic
    fun proxy(): OgCloudProxy =
        node as? OgCloudProxy ?: throw IllegalStateException("OgCloud is not running on a proxy here")

    /** Called by OgCloud's own plugin; not for other plugins. */
    @OgCloudInternalApi
    @JvmStatic
    fun install(implementation: OgCloudNode?) {
        node = implementation
    }
}

/** Marks what only OgCloud's own plugins call. */
@RequiresOptIn("Only OgCloud's own plugins call this.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class OgCloudInternalApi
