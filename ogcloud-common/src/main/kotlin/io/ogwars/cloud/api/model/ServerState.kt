package io.ogwars.cloud.api.model

enum class ServerState {
    REQUESTED,
    PREPARING,
    STARTING,
    RUNNING,
    DRAINING,
    STOPPING,
    STOPPED
}
