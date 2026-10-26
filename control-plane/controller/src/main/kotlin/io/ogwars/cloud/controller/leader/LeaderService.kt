package io.ogwars.cloud.controller.leader

/** Long-running leader-only work, such as informers: started when this replica wins the Lease, stopped when it loses it. */
interface LeaderService {
    fun startLeading()

    fun stopLeading()
}
