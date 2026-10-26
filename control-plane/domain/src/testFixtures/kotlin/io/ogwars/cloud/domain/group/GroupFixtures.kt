package io.ogwars.cloud.domain.group

import java.time.Duration

fun scaling(
    min: Int = 1,
    max: Int = 10,
    players: Int = 50,
    up: Double = 0.8,
    down: Double = 0.3,
    cooldown: Duration = Duration.ofSeconds(60),
) = ScalingPolicy(min, max, players, up, down, cooldown)

fun group(
    type: GroupType = GroupType.DYNAMIC,
    scaling: ScalingPolicy = scaling(max = if (type == GroupType.STATIC) 1 else 10),
    id: String = "lobby",
    drainTimeout: Duration = Duration.ofSeconds(60),
    maintenance: Boolean = false,
    profileId: String = defaultProfile(type),
) = Group(GroupId(id), type, profileId, scaling, drainTimeout, maintenance)

fun defaultProfile(type: GroupType) = if (type == GroupType.PROXY) "velocity-4.3.0" else "paper-26.3"
