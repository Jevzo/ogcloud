package io.ogwars.cloud.domain.user

/** What a web user or machine client may do through the API; [scope] is also the OAuth scope (D-0092). */
enum class ApiPermission(
    val scope: String,
) {
    OVERVIEW_READ("overview:read"),
    SERVERS_READ("servers:read"),
    SERVERS_COMMAND("servers:command"),
    GROUPS_READ("groups:read"),
    GROUPS_WRITE("groups:write"),
    PLAYERS_READ("players:read"),
    PLAYERS_WRITE("players:write"),
    NETWORK_READ("network:read"),
    NETWORK_WRITE("network:write"),
    PERMISSIONS_READ("permissions:read"),
    PERMISSIONS_WRITE("permissions:write"),
    TEMPLATES_READ("templates:read"),
    TEMPLATES_WRITE("templates:write"),
    NPCS_READ("npcs:read"),
    NPCS_WRITE("npcs:write"),
    LOGS_READ("logs:read"),
    USERS_READ("users:read"),
    USERS_WRITE("users:write"),
    ROLES_READ("roles:read"),
    ROLES_WRITE("roles:write"),
    OAUTH_CLIENTS_READ("oauth-clients:read"),
    OAUTH_CLIENTS_WRITE("oauth-clients:write"),
    PRIVACY_EXPORT("privacy:export"),
    PRIVACY_ERASE("privacy:erase"),
    ;

    val area: String get() = scope.substringBefore(':')

    val readOnly: Boolean get() = scope.endsWith(":read")

    companion object {
        private val byScope = entries.associateBy(ApiPermission::scope)

        /** Null for scopes that are not permissions, such as `openid`. */
        fun fromScope(scope: String): ApiPermission? = byScope[scope]

        /** Parses permissions given by a caller; unknown ones are an error, not ignored. */
        fun parse(scopes: Collection<String>): Set<ApiPermission> =
            scopes.map { fromScope(it) ?: throw IllegalArgumentException("Unknown permission '$it'") }.toSet()
    }
}

/** Areas only owners may touch (D-0089). */
private val OWNER_ONLY_AREAS = setOf("users", "roles", "oauth-clients", "privacy")

/** Roles whose permissions are defined here rather than in the database (D-0089); V2 seeds their ids. */
enum class BuiltInRole(
    val id: String,
    val permissions: Set<ApiPermission>,
) {
    OWNER("owner", ApiPermission.entries.toSet()),
    ADMIN("admin", ApiPermission.entries.filterNot { it.area in OWNER_ONLY_AREAS }.toSet()),
    MODERATOR(
        "moderator",
        setOf(
            ApiPermission.OVERVIEW_READ,
            ApiPermission.SERVERS_READ,
            ApiPermission.SERVERS_COMMAND,
            ApiPermission.PLAYERS_READ,
            ApiPermission.PLAYERS_WRITE,
            ApiPermission.GROUPS_READ,
            ApiPermission.LOGS_READ,
        ),
    ),
    VIEWER("viewer", ApiPermission.entries.filter(ApiPermission::readOnly).toSet()),
    ;

    companion object {
        fun find(id: String): BuiltInRole? = entries.firstOrNull { it.id == id }
    }
}
