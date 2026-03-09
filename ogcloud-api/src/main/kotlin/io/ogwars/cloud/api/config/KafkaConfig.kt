package io.ogwars.cloud.api.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    @Bean
    fun serverRequestTopic(): NewTopic = buildTopic(SERVER_REQUEST)

    @Bean
    fun serverStopTopic(): NewTopic = buildTopic(SERVER_STOP)

    @Bean
    fun networkUpdateTopic(): NewTopic = buildTopic(NETWORK_UPDATE, SINGLE_TOPIC_PARTITION)

    @Bean
    fun groupUpdateTopic(): NewTopic = buildTopic(GROUP_UPDATE)

    @Bean
    fun serverTemplatePushTopic(): NewTopic = buildTopic(SERVER_TEMPLATE_PUSH)

    @Bean
    fun permissionUpdateTopic(): NewTopic = buildTopic(PERMISSION_UPDATE)

    @Bean
    fun permissionGroupUpdatedTopic(): NewTopic = buildTopic(PERMISSION_GROUP_UPDATED)

    @Bean
    fun defaultPermissionGroupChangedTopic(): NewTopic = buildTopic(DEFAULT_PERMISSION_GROUP_CHANGED)

    @Bean
    fun playerTransferTopic(): NewTopic = buildTopic(PLAYER_TRANSFER)

    @Bean
    fun serverKillTopic(): NewTopic = buildTopic(SERVER_KILL)

    @Bean
    fun commandExecuteTopic(): NewTopic = buildTopic(COMMAND_EXECUTE)

    @Bean
    fun webAccountLinkOtpTopic(): NewTopic = buildTopic(WEB_ACCOUNT_LINK_OTP)

    private fun buildTopic(
        name: String,
        partitions: Int = DEFAULT_TOPIC_PARTITIONS,
    ): NewTopic =
        TopicBuilder
            .name(name)
            .partitions(partitions)
            .replicas(DEFAULT_TOPIC_REPLICAS)
            .build()

    companion object {
        const val SERVER_REQUEST = "ogcloud.server.request"
        const val SERVER_STOP = "ogcloud.server.stop"
        const val NETWORK_UPDATE = "ogcloud.network.update"
        const val GROUP_UPDATE = "ogcloud.group.update"
        const val SERVER_TEMPLATE_PUSH = "ogcloud.server.template.push"
        const val PERMISSION_UPDATE = "ogcloud.permission.update"
        const val PERMISSION_GROUP_UPDATED = "ogcloud.permission.group.updated"
        const val DEFAULT_PERMISSION_GROUP_CHANGED = "ogcloud.permission.group.default.changed"
        const val PLAYER_TRANSFER = "ogcloud.player.transfer"
        const val SERVER_KILL = "ogcloud.server.kill"
        const val COMMAND_EXECUTE = "ogcloud.command.execute"
        const val WEB_ACCOUNT_LINK_OTP = "ogcloud.web.account.link.otp"

        private const val DEFAULT_TOPIC_PARTITIONS = 3
        private const val SINGLE_TOPIC_PARTITION = 1
        private const val DEFAULT_TOPIC_REPLICAS = 1
    }
}
