plugins {
    id("ogcloud.kotlin-library")
}

// Built against the oldest Bukkit API OgCloud supports, so Paper and PandaSpigot share it (D-0138).
dependencies {
    api(project(":integrations:npc"))
    compileOnly(libs.pandaspigot.api)
    compileOnly(libs.packetevents.api)
    // Tests run the shared code on Paper's API through MockBukkit, the newest line it must work on.
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.packetevents.api)
}
