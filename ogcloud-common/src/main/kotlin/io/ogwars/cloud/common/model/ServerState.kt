package io.ogwars.cloud.common.model

enum class ServerState {
    REQUESTED,
    PREPARING,
    STARTING,
    RUNNING,
    DRAINING,
    STOPPING,
    STOPPED,
}
