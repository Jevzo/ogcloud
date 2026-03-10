package io.ogwars.cloud.velocity.message

object VelocityMessages {
    object Prefix {
        const val COMMAND = "&8| &6OgCloud &7> "
        const val ADMIN = "&8| &6OgCloud &7> "
        const val WEB = "&8| &6OgCloud Web &7> "
    }

    object Common {
        const val NONE = "none"
        const val NO_IP = "no IP"
        const val UNKNOWN = "unknown"
    }

    object Command {
        const val OGCLOUD_INFO = "This server is running on OgCloud <https://ogcloud.dev/>"
        const val FAILURE_TEMPLATE = "Failed: %error%"
        const val FAILURE_UNKNOWN_ERROR = "Unknown error"

        object Group {
            const val LIST_FETCHING = "Fetching groups..."
            const val LIST_EMPTY = "No groups found."
            const val LIST_HEADER = "&fGroups (%count%):"
            const val LIST_ENTRY =
                " &8- &f%group_id% &7(%group_type%)%maintenance_marker% &8instances: &f%min_online%-%max_instances%"
            const val LIST_MAINTENANCE_MARKER = " &c[MAINT]"

            const val INFO_HEADER = "&fGroup: %group_id%"
            const val INFO_TYPE = " &7Type: &f%group_type%"
            const val INFO_MAINTENANCE = " &7Maintenance: &f%maintenance%"
            const val INFO_INSTANCES = " &7Instances: &f%min_online%-%max_instances%"
            const val INFO_TEMPLATE = " &7Template: &f%template_path%/%template_version%"
            const val INFO_IMAGE = " &7Image: &f%server_image%"

            const val MAINTENANCE_SETTING = "Setting group '%group_id%' maintenance to %enabled%..."
            const val MAINTENANCE_UPDATED = "&aGroup maintenance updated."
        }

        object Network {
            const val MAINTENANCE_SETTING = "Setting network maintenance to %enabled%..."
            const val MAINTENANCE_UPDATED = "&aNetwork maintenance updated."

            const val INFO_HEADER = "&fNetwork Settings:"
            const val INFO_MAINTENANCE = " &7Maintenance: &f%maintenance%"
            const val INFO_MAX_PLAYERS = " &7Max Players: &f%max_players%"
            const val INFO_DEFAULT_GROUP = " &7Default Group: &f%default_group%"
            const val INFO_MOTD = " &7MOTD: &f%motd%"
            const val INFO_VERSION = " &7Version: &f%version%"
        }

        object Permission {
            object Group {
                const val LIST_EMPTY = "No permission groups found."
                const val LIST_HEADER = "&fPermission Groups (%count%):"
                const val LIST_ENTRY =
                    " &8- &f%group_id% &7(%group_name%) weight: %weight%%default_marker% &8perms: &f%permission_count%"
                const val LIST_DEFAULT_MARKER = " &a[default]"

                const val INFO_HEADER = "&fPermission Group: %group_id%"
                const val INFO_NAME = " &7Name: &f%group_name%"
                const val INFO_WEIGHT = " &7Weight: &f%weight%"
                const val INFO_DEFAULT = " &7Default: &f%is_default%"
                const val INFO_PREFIX = " &7Prefix: &f%chat_prefix%"
                const val INFO_SUFFIX = " &7Suffix: &f%chat_suffix%"
                const val INFO_PERMISSIONS_HEADER = " &7Permissions (%count%):"
                const val INFO_PERMISSION_ENTRY = "   &8- &f%permission%"
            }

            object Player {
                const val INFO_HEADER = "&fPlayer: %player_name%"
                const val INFO_GROUP = " &7Group: &f%group%"
                const val INFO_DURATION = " &7Duration: &f%duration%"
                const val DURATION_PERMANENT = "permanent"
                const val SET_SUCCESS = "&aSet %player_name%'s group to '%group%'."
                const val NOT_FOUND_ONLINE = "Player '%player_name%' not found online."
            }
        }

        object Player {
            const val FIND_NOT_FOUND = "Player '%player_name%' not found online."
            const val FIND_HEADER = "&fPlayer: %player_name%"
            const val FIND_UUID = " &7UUID: &f%uuid%"
            const val FIND_SERVER = " &7Server: &f%server%"
            const val FIND_PROXY = " &7Proxy: &f%proxy%"

            const val LIST_SERVER_NOT_FOUND = "Server '%server%' not found."
            const val LIST_FETCHING = "Fetching players..."
            const val LIST_EMPTY = "No online players found."
            const val LIST_HEADER = "&fOnline players (%count%):"
            const val LIST_ENTRY = " &8- &f%player_name% &7server: &f%server%"

            const val TRANSFER_NOT_FOUND = "Player '%player_name%' not found online."
            const val TRANSFER_REQUESTING = "Transferring '%player_name%' to '%target%'..."
            const val TRANSFER_REQUESTED = "&aTransfer requested."
        }

        object Server {
            const val LIST_FETCHING = "Fetching servers..."
            const val LIST_EMPTY = "No servers found."
            const val LIST_HEADER = "&fServers (%count%):"
            const val LIST_ENTRY = " &8- &f%display_name% &7[%state%] &8players: &f%player_count%"

            const val NOT_FOUND = "Server '%server%' not found."

            const val INFO_HEADER = "&fServer: %display_name%"
            const val INFO_ID = " &7ID: &f%id%"
            const val INFO_GROUP = " &7Group: &f%group%"
            const val INFO_STATE = " &7State: &f%state%"
            const val INFO_PLAYERS = " &7Players: &f%online%/%max%"
            const val INFO_TPS = " &7TPS: &f%tps%"
            const val INFO_MEMORY = " &7Memory: &f%memory_mb%MB"
            const val INFO_POD = " &7Pod: &f%pod_name% &7(%pod_ip%)"

            const val REQUEST_REQUESTING = "Requesting server in group '%group%'..."
            const val REQUEST_SUCCESS = "&aServer requested successfully."

            const val STOP_REQUESTING = "Stopping server '%display_name%'..."
            const val STOP_SUCCESS = "&aServer stop requested."

            const val KILL_REQUESTING = "Killing server '%display_name%'..."
            const val KILL_SUCCESS = "&aServer killed."

            const val TEMPLATE_PUSH_REQUESTING = "Forcing template push for '%display_name%'..."
            const val TEMPLATE_PUSH_SUCCESS = "&aTemplate push requested."
        }

        object RemoteCommand {
            const val EXECUTE_REQUESTING = "Executing '%command%' on %target_type% '%target_input%'..."
            const val EXECUTE_DISPATCHED = "&aCommand dispatched."
        }
    }

    object Listener {
        object ConnectionFailure {
            const val CONNECTION_LOST = "&cThe server you were connecting to went down."
            const val NO_SERVERS = "&cNo available servers. Please try again later."
        }

        object GroupUpdate {
            const val PROXY_MAINTENANCE = "&cProxy is in maintenance"
            const val SERVER_MAINTENANCE = "&cServer is in maintenance"
        }

        object PlayerConnection {
            const val PROXY_FULL = "&cThis proxy is full."
            const val CONNECT_FAILURE = "&cConnection failed due to backend communication issues. Please retry."
        }

        object PlayerTransfer {
            const val SHUTDOWN = "&cServer shutting down"
            const val MAINTENANCE = "&cServer is in maintenance"
            const val NO_SERVERS = "&cNo available servers. Please try again later."
        }

        object WebLink {
            const val OTP = "%prefix%&7Link code for &f%email%&7: &a%otp%"
        }
    }

    object Notification {
        const val SERVER_LIFECYCLE = "%prefix%Server &f%display_name% &7%state% &8(group: &f%group%&8)"
        const val NETWORK_MAINTENANCE = "%prefix%Network maintenance %status%"
        const val GROUP_MAINTENANCE = "%prefix%Group &f\"%group%\" &7maintenance %status%"

        const val STATUS_ENABLED = "&cENABLED"
        const val STATUS_DISABLED = "&aDisabled"
    }

    fun format(
        template: String,
        vararg placeholders: Pair<String, Any?>,
    ): String {
        if (placeholders.isEmpty()) {
            return template
        }

        var formatted = template
        placeholders.forEach { (name, value) ->
            formatted = formatted.replace("%$name%", value?.toString().orEmpty())
        }
        return formatted
    }
}
