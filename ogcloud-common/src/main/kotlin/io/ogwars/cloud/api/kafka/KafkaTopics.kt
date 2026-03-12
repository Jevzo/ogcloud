package io.ogwars.cloud.api.kafka

object KafkaTopics {
    const val SERVER_LIFECYCLE = "ogcloud.server.lifecycle"
    const val SERVER_REQUEST = "ogcloud.server.request"
    const val SERVER_STOP = "ogcloud.server.stop"
    const val SERVER_HEARTBEAT = "ogcloud.server.heartbeat"
    const val PROXY_HEARTBEAT = "ogcloud.proxy.heartbeat"
    const val NETWORK_UPDATE = "ogcloud.network.update"
    const val SERVER_GAMESTATE = "ogcloud.server.gamestate"
    const val PLAYER_TRANSFER = "ogcloud.player.transfer"
    const val GROUP_UPDATE = "ogcloud.group.update"
    const val SERVER_TEMPLATE_PUSH = "ogcloud.server.template.push"
    const val PLAYER_CONNECT = "ogcloud.player.connect"
    const val PLAYER_DISCONNECT = "ogcloud.player.disconnect"
    const val PLAYER_SWITCH = "ogcloud.player.switch"
    const val PERMISSION_UPDATE = "ogcloud.permission.update"
    const val PERMISSION_GROUP_UPDATED = "ogcloud.permission.group.updated"
    const val DEFAULT_PERMISSION_GROUP_CHANGED = "ogcloud.permission.group.default.changed"
    const val PERMISSION_EXPIRY = "ogcloud.permission.expiry"
    const val SERVER_KILL = "ogcloud.server.kill"
    const val COMMAND_EXECUTE = "ogcloud.command.execute"
    const val WEB_ACCOUNT_LINK_OTP = "ogcloud.web.account.link.otp"

    fun retryTopic(sourceTopic: String): String = "$sourceTopic$RETRY_SUFFIX"

    fun dltTopic(sourceTopic: String): String = "$sourceTopic$DLT_SUFFIX"

    private const val RETRY_SUFFIX = ".retry"
    private const val DLT_SUFFIX = ".dlt"
}
