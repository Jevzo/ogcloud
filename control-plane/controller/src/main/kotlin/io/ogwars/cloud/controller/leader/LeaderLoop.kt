package io.ogwars.cloud.controller.leader

import java.time.Duration

/** A singleton loop that runs only on the elected leader (D-0007), e.g. the autoscaler or the cleanup loop. */
interface LeaderLoop {
    val name: String
    val interval: Duration

    fun runOnce()
}
