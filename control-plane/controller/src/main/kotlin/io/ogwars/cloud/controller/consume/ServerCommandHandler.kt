package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.contracts.events.v1.ServerCommand.CommandCase
import io.ogwars.cloud.controller.lifecycle.LifecycleService
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.messaging.NonRetryableEventException
import io.ogwars.cloud.messaging.actor
import io.ogwars.cloud.messaging.toUuid
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** `ogcloud.server.command` from the API and the gateway; commands for unknown servers are no-ops. */
@Component
class ServerCommandHandler(
    private val lifecycle: LifecycleService,
    private val groups: GroupStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(command: ServerCommand) {
        val id = command.serverId.toUuid("server_id")
        val actor = command.metadata.actor()
        when (command.commandCase) {
            CommandCase.REQUEST -> {
                request(command, actor)
            }

            CommandCase.STOP -> {
                lifecycle.stop(id, actor, command.stop.reason.ifEmpty { DEFAULT_REASON })
            }

            CommandCase.KILL -> {
                lifecycle.kill(id, actor, command.kill.reason.ifEmpty { DEFAULT_REASON })
            }

            // The gateway that holds the server's template agent delivers pushes (D-0081).
            CommandCase.PUSH_TEMPLATE -> {}

            CommandCase.COMMAND_NOT_SET, null -> {
                throw NonRetryableEventException("Command for $id carries no command")
            }
        }
    }

    private fun request(
        command: ServerCommand,
        actor: Actor,
    ) {
        val groupId = command.request.groupId.toGroupId()
        val group = groups.find(groupId)?.value ?: throw NonRetryableEventException("Group $groupId does not exist")
        val server =
            lifecycle.request(
                command.serverId.toUuid("server_id"),
                group.id,
                group.type,
                group.profileId,
                actor,
            )
        // The lifecycle refuses a second STATIC server atomically; the request has nothing left to do.
        if (server == null) log.warn("Ignoring a request for STATIC group {}: its server already exists", groupId)
    }

    private fun String.toGroupId(): GroupId =
        try {
            GroupId(this)
        } catch (e: IllegalArgumentException) {
            throw NonRetryableEventException("Invalid group id '$this'", e)
        }

    private companion object {
        const val DEFAULT_REASON = "requested"
    }
}
