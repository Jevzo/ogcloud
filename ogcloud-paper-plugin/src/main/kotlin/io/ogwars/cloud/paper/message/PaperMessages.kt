package io.ogwars.cloud.paper.message

object PaperMessages {
    object Prefix {
        const val COMMAND = "&6OgCloud &8| "
    }

    object Common {
        const val CLEAR = "clear"
        const val NONE = "none"
        const val OFF = "off"
        const val NETWORK_DEFAULT = "network-default"
        const val UNKNOWN_ERROR = "unknown error"
    }

    object Chat {
        const val FORMAT = "%prefix%%name_color%%player_name%%suffix%%message%"
        const val DEFAULT_NAME_COLOR = "&7"
        const val DEFAULT_SUFFIX = ": &f"
    }

    object Command {
        object Npc {
            const val MISSING_PERMISSION = "Missing permission: %permission%"
            const val ROOT_USAGE = "Use /ogcloud npc ..."
            const val SUBCOMMAND_USAGE =
                "Subcommands: list, info, create, delete, movehere, teleport, title, subtitle, model, skin, lookat, leftaction, rightaction"
            const val SKIN_USAGE = "Usage: /ogcloud npc skin <id> clear"

            const val LIST_FAILURE = "Failed to list NPCs"
            const val LIST_EMPTY = "No NPCs found."
            const val LIST_HEADER = "NPCs: %count%"
            const val LIST_ENTRY =
                "&8- &f%npc_id% &7group=&f%group% &7world=&f%world% &7x=&f%x% &7y=&f%y% &7z=&f%z%"

            const val INFO_FAILURE = "Failed to fetch NPC info"
            const val INFO_LOOK_TARGET_MISSING = "You are not looking at a local NPC."
            const val INFO_HEADER = "NPC %npc_id%"
            const val INFO_GROUP = "&8- &7group: &f%group%"
            const val INFO_LOCATION =
                "&8- &7location: &f%world% &7(%x%, %y%, %z%) &7yaw=&f%yaw% &7pitch=&f%pitch%"
            const val INFO_TITLE = "&8- &7title: &f%title%"
            const val INFO_SUBTITLE = "&8- &7subtitle: &f%subtitle%"
            const val INFO_MODEL = "&8- &7model: &f%model%"
            const val INFO_SKIN = "&8- &7skin: &f%skin%"
            const val INFO_LOOK_AT = "&8- &7look-at: &f%enabled% &7radius=&f%radius%"
            const val INFO_LEFT = "&8- &7left: &f%action%"
            const val INFO_RIGHT = "&8- &7right: &f%action%"
            const val INFO_ACTION_TRANSFER = "transfer %group% (%strategy%)"
            const val INFO_SKIN_SIGNED = "signed"
            const val INFO_SKIN_UNSIGNED = "unsigned"

            const val CREATE_FAILURE = "Failed to create NPC"
            const val CREATE_SUCCESS = "Created NPC %npc_id% for group %group%."

            const val DELETE_FAILURE = "Failed to delete NPC"
            const val DELETE_SUCCESS = "Deleted NPC %npc_id%."

            const val UPDATE_FAILURE = "Failed to update NPC"
            const val MOVE_HERE_SUCCESS = "Moved NPC %npc_id% to your location."
            const val TITLE_SUCCESS = "Updated title for NPC %npc_id%."
            const val SUBTITLE_SUCCESS = "Updated subtitle for NPC %npc_id%."
            const val MODEL_SUCCESS = "Updated model for NPC %npc_id%."
            const val SKIN_CLEAR_SUCCESS = "Reset skin for NPC %npc_id% to the default profile."
            const val LOOK_AT_SUCCESS = "Updated look-at settings for NPC %npc_id%."
            const val ACTION_SUCCESS = "Updated %side% click action for NPC %npc_id%."

            const val TELEPORT_NOT_LOADED = "NPC %npc_id% is not loaded on this server."
            const val TELEPORT_WORLD_NOT_LOADED = "NPC world %world% is not loaded on this server."
            const val TELEPORT_SUCCESS = "Teleported to NPC %npc_id%."

            const val MODEL_INVALID = "Model must be steve or alex."
            const val LOOK_AT_INVALID = "lookat requires true or false."
            const val TEXT_VALUE_REQUIRED = "%field% requires text or off."
            const val ROUTING_STRATEGY_INVALID = "Invalid routing strategy: %strategy%"
            const val PLAYER_ONLY = "This command must be executed by a player."

            const val FAILURE_TEMPLATE = "%message%: %error%"
        }
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
