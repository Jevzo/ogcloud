package io.ogwars.cloud.common.event

import io.ogwars.cloud.common.model.RunningServer

data class ServerReadyEvent(
    val server: RunningServer,
)
