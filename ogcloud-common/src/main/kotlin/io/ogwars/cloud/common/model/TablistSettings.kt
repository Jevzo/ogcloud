package io.ogwars.cloud.common.model

data class TablistSettings(
    val header: String = "\n&6&lOgCloud Network\n&7Online: &a%onlinePlayers%&7/&a%maxPlayers%\n",
    val footer: String = "\n&7Server: &a%server% &8| &7Group: &a%group%\n&7Ping: &a%ping%ms\n",
)
