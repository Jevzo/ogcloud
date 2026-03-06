package io.ogwars.cloud.api.model

enum class WebUserRole {
    SERVICE,
    ADMIN,
    DEVELOPER;

    companion object {
        fun parse(value: String): WebUserRole {
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Unsupported role: $value")
        }
    }
}
