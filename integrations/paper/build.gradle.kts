plugins {
    id("ogcloud.mc-plugin")
}

dependencies {
    implementation(project(":integrations:node-core"))
    implementation(project(":integrations:bukkit"))
    // Paper provides its API, Adventure and Gson at runtime; the PacketEvents plugin provides its API (D-0137).
    compileOnly(libs.paper.api)
    compileOnly(libs.packetevents.api)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.packetevents.api)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}
