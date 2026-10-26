/** JDK every OgCloud JVM module compiles with; matches the JDK pinned in mise.toml (D-0034). */
internal const val PLATFORM_JAVA_VERSION = 25

/**
 * Bytecode level for code loaded by Minecraft servers and proxies. Lower than the platform JDK
 * because Velocity and PandaSpigot may run on Java 21 (D-0006).
 */
internal const val MINECRAFT_JAVA_VERSION = 21

internal const val REPOSITORY_URL = "https://github.com/Jevzo/ogcloud"
