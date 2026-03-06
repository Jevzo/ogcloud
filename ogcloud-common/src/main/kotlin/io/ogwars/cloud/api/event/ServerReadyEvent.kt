package io.ogwars.cloud.api.event

import io.ogwars.cloud.api.model.RunningServer

data class ServerReadyEvent(
    val server: RunningServer
)
