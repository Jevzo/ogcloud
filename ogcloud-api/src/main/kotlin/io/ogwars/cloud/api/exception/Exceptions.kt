package io.ogwars.cloud.api.exception

class GroupNotFoundException(
    name: String,
) : RuntimeException("Group not found: $name")

class GroupDeletionTimeoutException(
    name: String,
    activeServerIds: Collection<String>,
) : RuntimeException(
        "Group '$name' could not be deleted because servers are still active: ${
            activeServerIds.sorted().joinToString(", ")
        }",
    )

class GroupRestartTimeoutException(
    name: String,
    activeServerIds: Collection<String>,
) : RuntimeException(
        "Group '$name' could not be restarted because servers are still active: ${
            activeServerIds.sorted().joinToString(", ")
        }",
    )

class ServerNotFoundException(
    id: String,
) : RuntimeException("Server not found: $id")

class GroupAlreadyExistsException(
    name: String,
) : RuntimeException("Group already exists: $name")

class PermissionGroupNotFoundException(
    name: String,
) : RuntimeException("Permission group not found: $name")

class PermissionGroupAlreadyExistsException(
    name: String,
) : RuntimeException("Permission group already exists: $name")

class PlayerNotFoundException(
    id: String,
) : RuntimeException("Player not found: $id")

class PlayerNotOnlineException(
    id: String,
) : RuntimeException("Player not online: $id")

class TemplateNotFoundException(
    group: String,
    version: String,
) : RuntimeException("Template not found: group=$group, version=$version")

class WebUserNotFoundException(
    email: String,
) : RuntimeException("Web user not found: $email")

class WebUserAlreadyExistsException(
    email: String,
) : RuntimeException("Web user already exists: $email")

class InvalidCredentialsException : RuntimeException("Invalid credentials")

class InvalidRefreshTokenException : RuntimeException("Invalid refresh token")

class InvalidLinkOtpException : RuntimeException("Invalid or expired OTP")

class PlayerLinkUnavailableException(
    message: String,
) : RuntimeException(message)
