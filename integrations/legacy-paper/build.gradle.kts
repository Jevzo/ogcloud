plugins {
    id("ogcloud.mc-plugin")
}

dependencies {
    implementation(project(":integrations:bukkit"))
    // PandaSpigot provides its API at runtime; the PacketEvents plugin provides its API (D-0137).
    compileOnly(libs.pandaspigot.api)
    compileOnly(libs.packetevents.api)
    // PandaSpigot gets smoke tests only (D-0126); its own rules are unit-tested against the API with MockK.
    testImplementation(libs.pandaspigot.api)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}
