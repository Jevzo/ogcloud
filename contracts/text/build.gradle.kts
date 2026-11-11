plugins {
    id("ogcloud.kotlin-library")
}

dependencies {
    // Only the renderers use Adventure: the servers (on PandaSpigot the PacketEvents plugin) provide it, and the API
    // and the controller add it themselves (D-0202).
    compileOnly(libs.adventure.api)
    testImplementation(libs.adventure.api)
}
